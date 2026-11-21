package com.geofiber.geofiber_backend.services.cable;

import com.geofiber.geofiber_backend.dto.cable.LooseTubeRequest;
import com.geofiber.geofiber_backend.dto.cable.LooseTubeResponse;
import com.geofiber.geofiber_backend.entities.cable.CableAssetEntity;
import com.geofiber.geofiber_backend.entities.cable.LooseTubeEntity;
import com.geofiber.geofiber_backend.repositories.cable.CableRepository;
import com.geofiber.geofiber_backend.repositories.cable.FiberRepository;
import com.geofiber.geofiber_backend.repositories.cable.LooseTubeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.geofiber.geofiber_backend.services.common.ServiceErrors.badRequest;
import static com.geofiber.geofiber_backend.services.common.ServiceErrors.conflict;
import static com.geofiber.geofiber_backend.services.common.ServiceErrors.notFound;

@Service
@Transactional
public class LooseTubeService {
    private final CableRepository cableRepository;
    private final LooseTubeRepository looseTubeRepository;
    private final FiberRepository fiberRepository;

    public LooseTubeService(
        CableRepository cableRepository, LooseTubeRepository looseTubeRepository, FiberRepository fiberRepository
    ) {
        this.cableRepository = cableRepository;
        this.looseTubeRepository = looseTubeRepository;
        this.fiberRepository = fiberRepository;
    }

    public LooseTubeResponse create(UUID cableId, LooseTubeRequest request) {
        CableAssetEntity cable = cableRepository.findById(cableId).orElseThrow(notFound("Cable"));
        LooseTubeEntity tube = new LooseTubeEntity();
        tube.setCable(cable);
        requireCableCapacity(cable, looseTubeRepository.sumCapacityByCable(cableId) + request.capacity());
        return toResponse(looseTubeRepository.saveAndFlush(apply(tube, request)));
    }

    public LooseTubeResponse update(UUID cableId, UUID tubeId, LooseTubeRequest request) {
        LooseTubeEntity tube = getTube(cableId, tubeId);
        if (request.capacity() < fiberRepository.findHighestNumberInTube(tubeId)) {
            throw badRequest("Tube capacity is below its highest fiber number");
        }
        int othersCapacity = looseTubeRepository.sumCapacityByCable(cableId) - tube.getCapacity();
        requireCableCapacity(tube.getCable(), othersCapacity + request.capacity());
        return toResponse(looseTubeRepository.saveAndFlush(apply(tube, request)));
    }

    public void delete(UUID cableId, UUID tubeId) {
        LooseTubeEntity tube = getTube(cableId, tubeId);
        if (fiberRepository.existsByTube_Id(tubeId)) {
            throw conflict("Tube still has fibers");
        }
        looseTubeRepository.delete(tube);
    }

    @Transactional(readOnly = true)
    public List<LooseTubeResponse> findByCable(UUID cableId) {
        if (!cableRepository.existsById(cableId)) {
            throw notFound("Cable").get();
        }
        return looseTubeRepository.findAllByCable_IdOrderByTubeNumberAsc(cableId)
            .stream().map(this::toResponse).toList();
    }

    private static LooseTubeEntity apply(LooseTubeEntity tube, LooseTubeRequest request) {
        tube.setTubeNumber(request.tubeNumber());
        tube.setCapacity(request.capacity());
        tube.setColor(request.color());
        return tube;
    }

    private static void requireCableCapacity(CableAssetEntity cable, int totalTubeCapacity) {
        if (totalTubeCapacity > cable.getFiberCapacity()) {
            throw badRequest("Tube capacities exceed the cable fiber capacity of " + cable.getFiberCapacity());
        }
    }

    private LooseTubeEntity getTube(UUID cableId, UUID tubeId) {
        LooseTubeEntity tube = looseTubeRepository.findById(tubeId).orElseThrow(notFound("Loose tube"));
        if (!tube.getCable().getId().equals(cableId)) {
            throw notFound("Loose tube").get();
        }
        return tube;
    }

    private LooseTubeResponse toResponse(LooseTubeEntity tube) {
        return new LooseTubeResponse(
            tube.getId(), tube.getCable().getId(), tube.getTubeNumber(), tube.getCapacity(), tube.getColor()
        );
    }
}
