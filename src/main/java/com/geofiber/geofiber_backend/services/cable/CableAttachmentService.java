package com.geofiber.geofiber_backend.services.cable;

import com.geofiber.geofiber_backend.services.common.OwnershipGuard;
import com.geofiber.geofiber_backend.dto.cable.CableAttachmentRequest;
import com.geofiber.geofiber_backend.dto.cable.CableAttachmentResponse;
import com.geofiber.geofiber_backend.entities.cable.CableAssetEntity;
import com.geofiber.geofiber_backend.entities.cable.CableAttachmentEntity;
import com.geofiber.geofiber_backend.enums.cable.CableAttachmentRoleEnum;
import com.geofiber.geofiber_backend.repositories.box.BoxRepository;
import com.geofiber.geofiber_backend.repositories.cable.CableAttachmentRepository;
import com.geofiber.geofiber_backend.repositories.cable.CableRepository;
import com.geofiber.geofiber_backend.repositories.cable.FiberRepository;
import com.geofiber.geofiber_backend.repositories.olt.OltRepository;
import com.geofiber.geofiber_backend.repositories.termination.FiberTerminationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.geofiber.geofiber_backend.services.common.ServiceErrors.badRequest;
import static com.geofiber.geofiber_backend.services.common.ServiceErrors.conflict;
import static com.geofiber.geofiber_backend.services.common.ServiceErrors.notFound;

/**
 * Lista ordenada dos elementos (caixas e OLTs) por onde o cabo passa. Fibras só podem ser
 * ligadas em elementos dessa lista: a continuidade é declarada, nunca deduzida pelo mapa.
 */
@Service
@Transactional
public class CableAttachmentService {
    private final CableRepository cableRepository;
    private final CableAttachmentRepository attachmentRepository;
    private final BoxRepository boxRepository;
    private final OltRepository oltRepository;
    private final FiberTerminationRepository terminationRepository;
    private final FiberRepository fiberRepository;

    private final OwnershipGuard ownership;

    public CableAttachmentService(
        CableRepository cableRepository,
        CableAttachmentRepository attachmentRepository,
        BoxRepository boxRepository,
        OltRepository oltRepository,
        FiberTerminationRepository terminationRepository,
        FiberRepository fiberRepository,
        OwnershipGuard ownership
    ) {
        this.ownership = ownership;
        this.cableRepository = cableRepository;
        this.attachmentRepository = attachmentRepository;
        this.boxRepository = boxRepository;
        this.oltRepository = oltRepository;
        this.terminationRepository = terminationRepository;
        this.fiberRepository = fiberRepository;
    }

    /** Substitui a lista inteira. Vazia é permitida; senão: START, PASS_THROUGH*, END. */
    public List<CableAttachmentResponse> replace(UUID cableId, List<CableAttachmentRequest> request) {
        CableAssetEntity cable = cableRepository.findById(cableId).orElseThrow(notFound("Cable"));
        ownership.requireOwner(cable.getCreatedBy());
        validateShape(request);

        for (CableAttachmentEntity current : attachmentRepository.findAllByCable_IdOrderBySequenceNumberAsc(cableId)) {
            boolean kept = request.stream().anyMatch(item -> sameElement(current, item));
            if (!kept && hasConnections(cableId, current)) {
                throw conflict("Cable still has fibers connected at a detached element");
            }
        }

        attachmentRepository.deleteAllByCableId(cableId);
        List<CableAttachmentEntity> created = IntStream.range(0, request.size())
            .mapToObj(index -> newAttachment(cable, index + 1, request.get(index)))
            .toList();
        attachmentRepository.saveAllAndFlush(created);
        return created.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CableAttachmentResponse> findByCable(UUID cableId) {
        if (!cableRepository.existsById(cableId)) {
            throw notFound("Cable").get();
        }
        return attachmentRepository.findAllByCable_IdOrderBySequenceNumberAsc(cableId)
            .stream().map(this::toResponse).toList();
    }

    public void requireAttachedToBox(UUID cableId, UUID boxId) {
        if (!attachmentRepository.existsByCable_IdAndBox_Id(cableId, boxId)) {
            throw conflict("Cable is not attached to this box");
        }
    }

    public void requireAttachedToOlt(UUID cableId, UUID oltId) {
        if (!attachmentRepository.existsByCable_IdAndOlt_Id(cableId, oltId)) {
            throw conflict("Cable is not attached to this OLT");
        }
    }

    /** Variante por fibra: 404 se a fibra não existe, 409 se o cabo dela não passa pela caixa. */
    public void requireFiberAttachedToBox(UUID fiberId, UUID boxId) {
        requireAttachedToBox(cableOf(fiberId), boxId);
    }

    public void requireFiberAttachedToOlt(UUID fiberId, UUID oltId) {
        requireAttachedToOlt(cableOf(fiberId), oltId);
    }

    private UUID cableOf(UUID fiberId) {
        return fiberRepository.findById(fiberId).orElseThrow(notFound("Fiber")).getCable().getId();
    }

    private static void validateShape(List<CableAttachmentRequest> request) {
        if (request.isEmpty()) {
            return;
        }
        if (request.size() < 2) {
            throw badRequest("A cable needs a START and an END element");
        }
        Set<UUID> seen = new HashSet<>();
        for (int index = 0; index < request.size(); index++) {
            CableAttachmentRequest item = request.get(index);
            if ((item.boxId() == null) == (item.oltId() == null)) {
                throw badRequest("Each attachment must reference exactly one of boxId or oltId");
            }
            CableAttachmentRoleEnum expected = index == 0 ? CableAttachmentRoleEnum.START
                : index == request.size() - 1 ? CableAttachmentRoleEnum.END : CableAttachmentRoleEnum.PASS_THROUGH;
            if (item.role() != expected) {
                throw badRequest("Attachments must be START, then PASS_THROUGH elements, then END");
            }
            if (!seen.add(item.boxId() != null ? item.boxId() : item.oltId())) {
                throw badRequest("An element can appear only once in the cable");
            }
        }
    }

    private CableAttachmentEntity newAttachment(CableAssetEntity cable, int sequence, CableAttachmentRequest item) {
        CableAttachmentEntity attachment = new CableAttachmentEntity();
        attachment.setCable(cable);
        attachment.setSequenceNumber(sequence);
        attachment.setRole(item.role());
        if (item.boxId() != null) {
            attachment.setBox(boxRepository.findById(item.boxId()).orElseThrow(notFound("Box")));
        } else {
            attachment.setOlt(oltRepository.findById(item.oltId()).orElseThrow(notFound("OLT")));
        }
        return attachment;
    }

    private boolean hasConnections(UUID cableId, CableAttachmentEntity attachment) {
        return attachment.getBox() != null
            ? terminationRepository.existsCableConnectionInBox(cableId, attachment.getBox().getId())
            : terminationRepository.existsCableConnectionInOlt(cableId, attachment.getOlt().getId());
    }

    private static boolean sameElement(CableAttachmentEntity current, CableAttachmentRequest item) {
        return current.getBox() != null
            ? current.getBox().getId().equals(item.boxId())
            : current.getOlt().getId().equals(item.oltId());
    }

    private CableAttachmentResponse toResponse(CableAttachmentEntity attachment) {
        boolean isBox = attachment.getBox() != null;
        return new CableAttachmentResponse(
            attachment.getSequenceNumber(), attachment.getRole(),
            isBox ? attachment.getBox().getId() : null,
            isBox ? null : attachment.getOlt().getId(),
            isBox ? attachment.getBox().getName() : attachment.getOlt().getName());
    }
}
