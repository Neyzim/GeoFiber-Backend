package com.geofiber.geofiber_backend.services.olt;

import com.geofiber.geofiber_backend.dto.olt.OltRequest;
import com.geofiber.geofiber_backend.dto.olt.OltResponse;
import com.geofiber.geofiber_backend.entities.olt.OltAssetEntity;
import com.geofiber.geofiber_backend.enums.InfrastructureStatusEnum;
import com.geofiber.geofiber_backend.repositories.olt.OltRepository;
import com.geofiber.geofiber_backend.repositories.olt.OltPortRepository;
import com.geofiber.geofiber_backend.repositories.cable.CableAttachmentRepository;
import com.geofiber.geofiber_backend.services.geo.GeometryMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class OltService {
    private final OltRepository oltRepository;

    private final OltPortRepository oltPortRepository;
    private final CableAttachmentRepository attachmentRepository;

    public OltService(
        OltRepository oltRepository, OltPortRepository oltPortRepository, CableAttachmentRepository attachmentRepository
    ) {
        this.oltRepository = oltRepository;
        this.oltPortRepository = oltPortRepository;
        this.attachmentRepository = attachmentRepository;
    }

    public OltResponse create(OltRequest request) {
        OltAssetEntity olt = new OltAssetEntity();
        apply(olt, request);
        return toResponse(oltRepository.saveAndFlush(olt));
    }

    @Transactional(readOnly = true)
    public List<OltResponse> findAll() {
        return oltRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OltResponse findById(UUID id) {
        return toResponse(getOlt(id));
    }

    public OltResponse update(UUID id, OltRequest request) {
        OltAssetEntity olt = getOlt(id);
        apply(olt, request);
        return toResponse(oltRepository.saveAndFlush(olt));
    }

    public void delete(UUID id) {
        OltAssetEntity olt = getOlt(id);
        if (oltPortRepository.existsByOlt_Id(id) || attachmentRepository.existsByOlt_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OLT still has ports or attached cables");
        }
        oltRepository.delete(olt);
    }

    private OltAssetEntity getOlt(UUID id) {
        return oltRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OLT not found"));
    }

    private void apply(OltAssetEntity olt, OltRequest request) {
        olt.setName(request.name());
        olt.setDescription(request.description());
        olt.setUsageStatus(request.usageStatus() == null
            ? InfrastructureStatusEnum.PLANNED : request.usageStatus());
        olt.setModel(request.model());
        olt.setLocation(GeometryMapper.toPoint(request.location()));
    }

    private OltResponse toResponse(OltAssetEntity olt) {
        return new OltResponse(
            olt.getId(), olt.getName(), olt.getDescription(), olt.getUsageStatus(),
            olt.getModel(), GeometryMapper.fromPoint(olt.getLocation())
        );
    }
}
