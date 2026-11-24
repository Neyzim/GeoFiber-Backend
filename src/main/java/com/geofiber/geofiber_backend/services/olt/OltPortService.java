package com.geofiber.geofiber_backend.services.olt;

import com.geofiber.geofiber_backend.entities.olt.OltAssetEntity;
import com.geofiber.geofiber_backend.services.common.OwnershipGuard;
import com.geofiber.geofiber_backend.dto.olt.OltPortRequest;
import com.geofiber.geofiber_backend.dto.olt.OltPortResponse;
import com.geofiber.geofiber_backend.entities.olt.OltPortEntity;
import com.geofiber.geofiber_backend.entities.termination.FiberTerminationEntity;
import com.geofiber.geofiber_backend.enums.FiberEndEnum;
import com.geofiber.geofiber_backend.repositories.olt.OltPortRepository;
import com.geofiber.geofiber_backend.repositories.olt.OltRepository;
import com.geofiber.geofiber_backend.repositories.termination.FiberTerminationRepository;
import com.geofiber.geofiber_backend.services.cable.CableAttachmentService;
import com.geofiber.geofiber_backend.services.common.PortStatusResolver;
import com.geofiber.geofiber_backend.services.termination.FiberTerminationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.geofiber.geofiber_backend.services.common.ServiceErrors.conflict;
import static com.geofiber.geofiber_backend.services.common.ServiceErrors.notFound;

@Service
@Transactional
public class OltPortService {
    private final OltRepository oltRepository;
    private final OltPortRepository oltPortRepository;
    private final FiberTerminationRepository terminationRepository;
    private final FiberTerminationService terminationService;
    private final CableAttachmentService cableAttachmentService;

    private final OwnershipGuard ownership;

    public OltPortService(
        OltRepository oltRepository,
        OltPortRepository oltPortRepository,
        FiberTerminationRepository terminationRepository,
        FiberTerminationService terminationService,
        CableAttachmentService cableAttachmentService,
        OwnershipGuard ownership
    ) {
        this.ownership = ownership;
        this.oltRepository = oltRepository;
        this.oltPortRepository = oltPortRepository;
        this.terminationRepository = terminationRepository;
        this.terminationService = terminationService;
        this.cableAttachmentService = cableAttachmentService;
    }

    public OltPortResponse create(UUID oltId, OltPortRequest request) {
        OltPortEntity port = new OltPortEntity();
        OltAssetEntity olt = oltRepository.findById(oltId).orElseThrow(notFound("OLT"));
        ownership.requireOwner(olt.getCreatedBy());
        port.setOlt(olt);
        return apply(port, null, request);
    }

    public OltPortResponse update(UUID oltId, UUID portId, OltPortRequest request) {
        OltPortEntity port = getPort(oltId, portId);
        ownership.requireOwner(port.getOlt().getCreatedBy());
        return apply(port, terminationRepository.findByOltPort_Id(portId).orElse(null), request);
    }

    public void delete(UUID oltId, UUID portId) {
        OltPortEntity port = getPort(oltId, portId);
        ownership.requireOwner(port.getOlt().getCreatedBy());
        if (terminationRepository.findByOltPort_Id(portId).isPresent()) {
            throw conflict("OLT port is connected");
        }
        oltPortRepository.delete(port);
    }

    @Transactional(readOnly = true)
    public List<OltPortResponse> findByOlt(UUID oltId) {
        if (!oltRepository.existsById(oltId)) {
            throw notFound("OLT").get();
        }
        List<OltPortEntity> ports = oltPortRepository.findAllByOlt_IdOrderBySlotNumberAscPortNumberAsc(oltId);
        Map<UUID, FiberTerminationEntity> terminations = terminationRepository
            .findAllByOltPort_IdIn(ports.stream().map(OltPortEntity::getId).toList()).stream()
            .collect(Collectors.toMap(t -> t.getOltPort().getId(), Function.identity()));
        return ports.stream().map(port -> toResponse(port, terminations.get(port.getId()))).toList();
    }

    private OltPortResponse apply(OltPortEntity port, FiberTerminationEntity current, OltPortRequest request) {
        port.setSlotNumber(request.slotNumber());
        port.setPortNumber(request.portNumber());
        oltPortRepository.save(port);

        if (request.fiberId() != null) {
            cableAttachmentService.requireFiberAttachedToOlt(request.fiberId(), port.getOlt().getId());
        }
        FiberEndEnum end = request.fiberEnd() == null ? FiberEndEnum.A : request.fiberEnd();
        FiberTerminationEntity linked = terminationService.relink(
            current, request.fiberId(), end, termination -> termination.setOltPort(port));
        port.setStatus(PortStatusResolver.resolve(request.status(), linked != null));
        oltPortRepository.flush();
        return toResponse(port, linked);
    }

    private OltPortEntity getPort(UUID oltId, UUID portId) {
        OltPortEntity port = oltPortRepository.findById(portId).orElseThrow(notFound("OLT port"));
        if (!port.getOlt().getId().equals(oltId)) {
            throw notFound("OLT port").get();
        }
        return port;
    }

    private OltPortResponse toResponse(OltPortEntity port, FiberTerminationEntity termination) {
        return new OltPortResponse(
            port.getId(), port.getOlt().getId(), port.getSlotNumber(), port.getPortNumber(), port.getStatus(),
            termination == null ? null : termination.getFiber().getId(),
            termination == null ? null : termination.getFiberEnd()
        );
    }
}
