package com.geofiber.geofiber_backend.services.connection;

import com.geofiber.geofiber_backend.services.common.OwnershipGuard;

import com.geofiber.geofiber_backend.dto.connection.ConnectionRequest;
import com.geofiber.geofiber_backend.dto.connection.ConnectionResponse;
import com.geofiber.geofiber_backend.entities.box.BoxAssetEntity;
import com.geofiber.geofiber_backend.entities.connection.OpticalConnectionEntity;
import com.geofiber.geofiber_backend.entities.termination.FiberTerminationEntity;
import com.geofiber.geofiber_backend.enums.OpticalConnectionTypeEnum;
import com.geofiber.geofiber_backend.repositories.box.BoxRepository;
import com.geofiber.geofiber_backend.repositories.connection.OpticalConnectionRepository;
import com.geofiber.geofiber_backend.services.cable.CableAttachmentService;
import com.geofiber.geofiber_backend.services.termination.FiberTerminationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.geofiber.geofiber_backend.services.common.ServiceErrors.badRequest;
import static com.geofiber.geofiber_backend.services.common.ServiceErrors.conflict;
import static com.geofiber.geofiber_backend.services.common.ServiceErrors.notFound;

/** Conexões por conector numa caixa. Fusões são criadas e removidas pelas emendas. */
@Service
@Transactional
public class ConnectionService {
    private final BoxRepository boxRepository;
    private final OpticalConnectionRepository connectionRepository;
    private final FiberTerminationService terminationService;
    private final CableAttachmentService cableAttachmentService;

    private final OwnershipGuard ownership;

    public ConnectionService(
        BoxRepository boxRepository,
        OpticalConnectionRepository connectionRepository,
        FiberTerminationService terminationService,
        CableAttachmentService cableAttachmentService,
        OwnershipGuard ownership
    ) {
        this.ownership = ownership;
        this.boxRepository = boxRepository;
        this.connectionRepository = connectionRepository;
        this.terminationService = terminationService;
        this.cableAttachmentService = cableAttachmentService;
    }

    public ConnectionResponse create(UUID boxId, ConnectionRequest request) {
        if (request.fiberAId().equals(request.fiberBId())) {
            throw badRequest("A connection must join two different fibers");
        }
        BoxAssetEntity box = boxRepository.findById(boxId).orElseThrow(notFound("Box"));
        cableAttachmentService.requireFiberAttachedToBox(request.fiberAId(), boxId);
        cableAttachmentService.requireFiberAttachedToBox(request.fiberBId(), boxId);
        FiberTerminationEntity first = terminationService.getAvailable(request.fiberAId(), request.fiberAEnd());
        FiberTerminationEntity second = terminationService.getAvailable(request.fiberBId(), request.fiberBEnd());
        ownership.requireOwnerOfAny(first.getFiber().getCable().getCreatedBy(), second.getFiber().getCable().getCreatedBy());
        OpticalConnectionEntity connection = terminationService.connect(
            first, second, OpticalConnectionTypeEnum.CONNECTOR, box, request.attenuationDb());
        connectionRepository.flush();
        return toResponse(connection, request.fiberAId());
    }

    @Transactional(readOnly = true)
    public List<ConnectionResponse> findByBox(UUID boxId) {
        if (!boxRepository.existsById(boxId)) {
            throw notFound("Box").get();
        }
        return connectionRepository.findAllByBox_IdAndType(boxId, OpticalConnectionTypeEnum.CONNECTOR).stream()
            .map(connection -> toResponse(connection, null))
            .toList();
    }

    public void delete(UUID boxId, UUID connectionId) {
        OpticalConnectionEntity connection = connectionRepository.findById(connectionId)
            .orElseThrow(notFound("Connection"));
        if (!connection.getBox().getId().equals(boxId)) {
            throw notFound("Connection").get();
        }
        ownership.requireOwnerOfAny(
            connection.getTerminationA().getFiber().getCable().getCreatedBy(),
            connection.getTerminationB().getFiber().getCable().getCreatedBy());
        if (connection.getType() == OpticalConnectionTypeEnum.FUSION) {
            throw conflict("Fusions are removed through the splice endpoint");
        }
        terminationService.release(connection.getTerminationA());
        terminationService.release(connection.getTerminationB());
        connectionRepository.delete(connection);
        connectionRepository.flush();
    }

    /**
     * O banco guarda as pontas em ordem de UUID; {@code firstFiberId} (quando conhecido) faz a
     * resposta devolver a fibra A na mesma posição em que veio na requisição.
     */
    private ConnectionResponse toResponse(OpticalConnectionEntity connection, UUID firstFiberId) {
        FiberTerminationEntity a = connection.getTerminationA();
        FiberTerminationEntity b = connection.getTerminationB();
        if (firstFiberId != null && !a.getFiber().getId().equals(firstFiberId)) {
            FiberTerminationEntity swap = a;
            a = b;
            b = swap;
        }
        return new ConnectionResponse(
            connection.getId(), connection.getBox().getId(), connection.getType(),
            a.getFiber().getId(), a.getFiberEnd(), b.getFiber().getId(), b.getFiberEnd(),
            connection.getAttenuationDb());
    }
}
