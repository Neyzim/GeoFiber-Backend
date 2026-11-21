package com.geofiber.geofiber_backend.services.cable;

import com.geofiber.geofiber_backend.dto.cable.CableRequest;
import com.geofiber.geofiber_backend.dto.cable.CableResponse;
import com.geofiber.geofiber_backend.entities.cable.CableAssetEntity;
import com.geofiber.geofiber_backend.enums.InfrastructureStatusEnum;
import com.geofiber.geofiber_backend.repositories.cable.CableRepository;
import com.geofiber.geofiber_backend.repositories.cable.LooseTubeRepository;
import com.geofiber.geofiber_backend.services.geo.GeometryMapper;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CableService {
    private final CableRepository cableRepository;
    private final EntityManager entityManager;
    private final LooseTubeRepository looseTubeRepository;

    public CableService(
        CableRepository cableRepository, EntityManager entityManager, LooseTubeRepository looseTubeRepository
    ) {
        this.looseTubeRepository = looseTubeRepository;
        this.cableRepository = cableRepository;
        this.entityManager = entityManager;
    }

    public CableResponse create(CableRequest request) {
        CableAssetEntity cable = new CableAssetEntity();
        apply(cable, request);
        cableRepository.saveAndFlush(cable);
        entityManager.refresh(cable);
        return toResponse(cable);
    }

    @Transactional(readOnly = true)
    public List<CableResponse> findAll() {
        return cableRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CableResponse findById(UUID id) {
        return toResponse(getCable(id));
    }

    public CableResponse update(UUID id, CableRequest request) {
        CableAssetEntity cable = getCable(id);
        if (request.fiberCapacity() < looseTubeRepository.sumCapacityByCable(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cable fiber capacity is below the total capacity of its tubes");
        }
        apply(cable, request);
        cableRepository.saveAndFlush(cable);
        entityManager.refresh(cable);
        return toResponse(cable);
    }

    /** Exclui o cabo e sua lista de elementos (cascade no banco). Bloqueado se houver tubos. */
    public void delete(UUID id) {
        CableAssetEntity cable = getCable(id);
        if (looseTubeRepository.existsByCable_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cable still has tubes");
        }
        cableRepository.delete(cable);
    }

    private CableAssetEntity getCable(UUID id) {
        return cableRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cable not found"));
    }

    private void apply(CableAssetEntity cable, CableRequest request) {
        cable.setName(request.name());
        cable.setDescription(request.description());
        cable.setRole(request.role());
        cable.setUsageStatus(request.usageStatus() == null
            ? InfrastructureStatusEnum.PLANNED : request.usageStatus());
        cable.setManufacturer(request.manufacturer());
        cable.setModel(request.model());
        cable.setFiberCapacity(request.fiberCapacity());
        cable.setLocation(GeometryMapper.toLineString(request.coordinates()));
    }

    private CableResponse toResponse(CableAssetEntity cable) {
        return new CableResponse(
            cable.getId(), cable.getName(), cable.getDescription(), cable.getRole(),
            cable.getUsageStatus(), cable.getManufacturer(), cable.getModel(),
            cable.getFiberCapacity(), cable.getLengthMeters(),
            GeometryMapper.fromLineString(cable.getLocation())
        );
    }
}
