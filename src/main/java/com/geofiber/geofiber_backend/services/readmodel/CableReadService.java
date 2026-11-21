package com.geofiber.geofiber_backend.services.readmodel;

import com.geofiber.geofiber_backend.dto.readmodel.AttachedCableResponse;
import com.geofiber.geofiber_backend.dto.readmodel.CableFiberResponse;
import com.geofiber.geofiber_backend.dto.readmodel.FiberEndLinkResponse;
import com.geofiber.geofiber_backend.dto.readmodel.FiberEndResponse;
import com.geofiber.geofiber_backend.entities.cable.CableAssetEntity;
import com.geofiber.geofiber_backend.entities.cable.CableAttachmentEntity;
import com.geofiber.geofiber_backend.entities.cable.FiberEntity;
import com.geofiber.geofiber_backend.entities.connection.OpticalConnectionEntity;
import com.geofiber.geofiber_backend.entities.splice.SpliceEntity;
import com.geofiber.geofiber_backend.entities.termination.FiberTerminationEntity;
import com.geofiber.geofiber_backend.enums.OpticalConnectionTypeEnum;
import com.geofiber.geofiber_backend.enums.readmodel.FiberEndLinkTypeEnum;
import com.geofiber.geofiber_backend.repositories.box.BoxRepository;
import com.geofiber.geofiber_backend.repositories.cable.CableAttachmentRepository;
import com.geofiber.geofiber_backend.repositories.cable.CableRepository;
import com.geofiber.geofiber_backend.repositories.cable.FiberRepository;
import com.geofiber.geofiber_backend.repositories.connection.OpticalConnectionRepository;
import com.geofiber.geofiber_backend.repositories.olt.OltRepository;
import com.geofiber.geofiber_backend.repositories.splice.SpliceRepository;
import com.geofiber.geofiber_backend.repositories.termination.FiberTerminationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.geofiber.geofiber_backend.services.common.ServiceErrors.notFound;

/** Consultas de leitura sobre cabos: onde estão anexados e o estado de cada fibra. */
@Service
@Transactional(readOnly = true)
public class CableReadService {
    private final CableRepository cableRepository;
    private final BoxRepository boxRepository;
    private final OltRepository oltRepository;
    private final CableAttachmentRepository attachmentRepository;
    private final FiberRepository fiberRepository;
    private final FiberTerminationRepository terminationRepository;
    private final OpticalConnectionRepository connectionRepository;
    private final SpliceRepository spliceRepository;

    public CableReadService(
        CableRepository cableRepository,
        BoxRepository boxRepository,
        OltRepository oltRepository,
        CableAttachmentRepository attachmentRepository,
        FiberRepository fiberRepository,
        FiberTerminationRepository terminationRepository,
        OpticalConnectionRepository connectionRepository,
        SpliceRepository spliceRepository
    ) {
        this.cableRepository = cableRepository;
        this.boxRepository = boxRepository;
        this.oltRepository = oltRepository;
        this.attachmentRepository = attachmentRepository;
        this.fiberRepository = fiberRepository;
        this.terminationRepository = terminationRepository;
        this.connectionRepository = connectionRepository;
        this.spliceRepository = spliceRepository;
    }

    public List<AttachedCableResponse> findByBox(UUID boxId) {
        if (!boxRepository.existsById(boxId)) {
            throw notFound("Box").get();
        }
        return attachmentRepository.findAllByBox_IdOrderByCable_NameAsc(boxId).stream()
            .map(CableReadService::toAttached).toList();
    }

    public List<AttachedCableResponse> findByOlt(UUID oltId) {
        if (!oltRepository.existsById(oltId)) {
            throw notFound("OLT").get();
        }
        return attachmentRepository.findAllByOlt_IdOrderByCable_NameAsc(oltId).stream()
            .map(CableReadService::toAttached).toList();
    }

    /** Fibras do cabo por número absoluto, com o que cada ponta liga. Consultas em lote, sem N+1. */
    public List<CableFiberResponse> findFibers(UUID cableId) {
        if (!cableRepository.existsById(cableId)) {
            throw notFound("Cable").get();
        }
        List<FiberEntity> fibers = fiberRepository.findAllByCable_IdOrderByAbsoluteNumberAsc(cableId);
        if (fibers.isEmpty()) {
            return List.of();
        }
        List<FiberTerminationEntity> ends = terminationRepository.findAllByFiber_IdIn(
            fibers.stream().map(FiberEntity::getId).toList());
        List<UUID> endIds = ends.stream().map(FiberTerminationEntity::getId).toList();
        List<OpticalConnectionEntity> connections =
            connectionRepository.findAllByTerminationA_IdInOrTerminationB_IdIn(endIds, endIds);
        Map<UUID, SpliceEntity> splicesByConnection = connections.isEmpty() ? Map.of()
            : spliceRepository.findAllByConnection_IdIn(connections.stream().map(OpticalConnectionEntity::getId).toList())
                .stream().collect(Collectors.toMap(splice -> splice.getConnection().getId(), Function.identity()));

        Map<UUID, FiberEndLinkResponse> connectionLinks = new HashMap<>();
        for (OpticalConnectionEntity connection : connections) {
            connectionLinks.put(connection.getTerminationA().getId(),
                connectionLink(connection, connection.getTerminationB(), splicesByConnection));
            connectionLinks.put(connection.getTerminationB().getId(),
                connectionLink(connection, connection.getTerminationA(), splicesByConnection));
        }
        Map<UUID, List<FiberEndResponse>> endsByFiber = ends.stream()
            .sorted(Comparator.comparing(FiberTerminationEntity::getFiberEnd))
            .collect(Collectors.groupingBy(end -> end.getFiber().getId(), Collectors.mapping(
                end -> new FiberEndResponse(end.getFiberEnd(), end.getStatus(), link(end, connectionLinks)),
                Collectors.toList())));

        return fibers.stream().map(fiber -> new CableFiberResponse(
            fiber.getId(), fiber.getTube().getId(), fiber.getTube().getTubeNumber(), fiber.getTube().getColor(),
            fiber.getNumberInTube(), fiber.getAbsoluteNumber(), fiber.getColor(), fiber.getStatus(),
            endsByFiber.getOrDefault(fiber.getId(), List.of())
        )).toList();
    }

    private static FiberEndLinkResponse link(FiberTerminationEntity end, Map<UUID, FiberEndLinkResponse> connections) {
        if (end.getOltPort() != null) {
            return new FiberEndLinkResponse(FiberEndLinkTypeEnum.OLT_PORT, end.getOltPort().getId(),
                end.getOltPort().getOlt().getId(), null, null);
        }
        if (end.getSplitterPort() != null) {
            return new FiberEndLinkResponse(FiberEndLinkTypeEnum.SPLITTER_PORT, end.getSplitterPort().getId(),
                end.getSplitterPort().getSplitter().getBox().getId(), null, null);
        }
        if (end.getServicePort() != null) {
            return new FiberEndLinkResponse(FiberEndLinkTypeEnum.SERVICE_PORT, end.getServicePort().getId(),
                end.getServicePort().getBox().getId(), null, null);
        }
        return connections.get(end.getId());
    }

    private static FiberEndLinkResponse connectionLink(
        OpticalConnectionEntity connection, FiberTerminationEntity peer, Map<UUID, SpliceEntity> splices
    ) {
        boolean fusion = connection.getType() == OpticalConnectionTypeEnum.FUSION;
        SpliceEntity splice = splices.get(connection.getId());
        // Fusões são gerenciadas pela emenda: devolve o id da emenda para o frontend poder excluí-la.
        UUID id = fusion && splice != null ? splice.getId() : connection.getId();
        return new FiberEndLinkResponse(
            fusion ? FiberEndLinkTypeEnum.FUSION : FiberEndLinkTypeEnum.CONNECTOR, id,
            connection.getBox().getId(), peer.getFiber().getId(), peer.getFiberEnd());
    }

    private static AttachedCableResponse toAttached(CableAttachmentEntity attachment) {
        CableAssetEntity cable = attachment.getCable();
        return new AttachedCableResponse(
            cable.getId(), cable.getName(), cable.getRole(), cable.getUsageStatus(), cable.getFiberCapacity(),
            attachment.getRole(), attachment.getSequenceNumber());
    }
}
