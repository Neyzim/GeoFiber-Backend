package com.geofiber.geofiber_backend.services.cable;

import com.geofiber.geofiber_backend.services.common.OwnershipGuard;
import com.geofiber.geofiber_backend.dto.cable.FiberRequest;
import com.geofiber.geofiber_backend.dto.cable.FiberResponse;
import com.geofiber.geofiber_backend.entities.cable.FiberEntity;
import com.geofiber.geofiber_backend.entities.cable.LooseTubeEntity;
import com.geofiber.geofiber_backend.enums.FiberStatusEnum;
import com.geofiber.geofiber_backend.enums.TerminationStatusEnum;
import com.geofiber.geofiber_backend.repositories.cable.FiberRepository;
import com.geofiber.geofiber_backend.repositories.cable.LooseTubeRepository;
import com.geofiber.geofiber_backend.repositories.termination.FiberTerminationRepository;
import com.geofiber.geofiber_backend.services.termination.FiberTerminationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.geofiber.geofiber_backend.services.common.ServiceErrors.badRequest;
import static com.geofiber.geofiber_backend.services.common.ServiceErrors.conflict;
import static com.geofiber.geofiber_backend.services.common.ServiceErrors.notFound;

@Service
@Transactional
public class FiberService {
    private final LooseTubeRepository looseTubeRepository;
    private final FiberRepository fiberRepository;
    private final FiberTerminationRepository terminationRepository;
    private final FiberTerminationService terminationService;

    private final OwnershipGuard ownership;

    public FiberService(
        LooseTubeRepository looseTubeRepository,
        FiberRepository fiberRepository,
        FiberTerminationRepository terminationRepository,
        FiberTerminationService terminationService,
        OwnershipGuard ownership
    ) {
        this.ownership = ownership;
        this.looseTubeRepository = looseTubeRepository;
        this.fiberRepository = fiberRepository;
        this.terminationRepository = terminationRepository;
        this.terminationService = terminationService;
    }

    public FiberResponse create(UUID tubeId, FiberRequest request) {
        LooseTubeEntity tube = looseTubeRepository.findById(tubeId).orElseThrow(notFound("Loose tube"));
        ownership.requireOwner(tube.getCable().getCreatedBy());
        FiberEntity fiber = new FiberEntity();
        fiber.setCable(tube.getCable());
        fiber.setTube(tube);
        apply(fiber, request);
        fiberRepository.save(fiber);
        terminationService.createEnds(fiber);
        fiberRepository.flush();
        return toResponse(fiber);
    }

    public FiberResponse update(UUID tubeId, UUID fiberId, FiberRequest request) {
        FiberEntity fiber = getFiber(tubeId, fiberId);
        ownership.requireOwner(fiber.getCable().getCreatedBy());
        apply(fiber, request);
        return toResponse(fiberRepository.saveAndFlush(fiber));
    }

    /** Marca a fibra como disponível, rompida ou reservada. OCCUPIED vem das ligações. */
    public FiberResponse updateStatus(UUID tubeId, UUID fiberId, FiberStatusEnum status) {
        if (status == FiberStatusEnum.OCCUPIED) {
            throw badRequest("OCCUPIED is set automatically by connections");
        }
        FiberEntity fiber = getFiber(tubeId, fiberId);
        ownership.requireOwner(fiber.getCable().getCreatedBy());
        requireDisconnected(fiberId);
        fiber.setStatus(status);
        return toResponse(fiberRepository.saveAndFlush(fiber));
    }

    public void delete(UUID tubeId, UUID fiberId) {
        FiberEntity fiber = getFiber(tubeId, fiberId);
        ownership.requireOwner(fiber.getCable().getCreatedBy());
        requireDisconnected(fiberId);
        terminationRepository.deleteAll(terminationRepository.findAllByFiber_Id(fiberId));
        fiberRepository.delete(fiber);
    }

    @Transactional(readOnly = true)
    public List<FiberResponse> findByTube(UUID tubeId) {
        if (!looseTubeRepository.existsById(tubeId)) {
            throw notFound("Loose tube").get();
        }
        return fiberRepository.findAllByTube_IdOrderByNumberInTubeAsc(tubeId)
            .stream().map(this::toResponse).toList();
    }

    private static void apply(FiberEntity fiber, FiberRequest request) {
        if (request.numberInTube() > fiber.getTube().getCapacity()
            || request.absoluteNumber() > fiber.getCable().getFiberCapacity()) {
            throw badRequest("Fiber number exceeds its cable or tube capacity");
        }
        fiber.setNumberInTube(request.numberInTube());
        fiber.setAbsoluteNumber(request.absoluteNumber());
        fiber.setColor(request.color());
    }

    private void requireDisconnected(UUID fiberId) {
        boolean connected = terminationRepository.findAllByFiber_Id(fiberId).stream()
            .anyMatch(end -> end.getStatus() == TerminationStatusEnum.CONNECTED);
        if (connected) {
            throw conflict("Fiber is connected");
        }
    }

    private FiberEntity getFiber(UUID tubeId, UUID fiberId) {
        FiberEntity fiber = fiberRepository.findById(fiberId).orElseThrow(notFound("Fiber"));
        if (!fiber.getTube().getId().equals(tubeId)) {
            throw notFound("Fiber").get();
        }
        return fiber;
    }

    private FiberResponse toResponse(FiberEntity fiber) {
        return new FiberResponse(
            fiber.getId(), fiber.getCable().getId(), fiber.getTube().getId(),
            fiber.getNumberInTube(), fiber.getAbsoluteNumber(), fiber.getColor(), fiber.getStatus()
        );
    }
}
