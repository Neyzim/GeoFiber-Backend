package com.geofiber.geofiber_backend.services.box;

import com.geofiber.geofiber_backend.services.common.OwnershipGuard;

import com.geofiber.geofiber_backend.dto.box.BoxRequest;
import com.geofiber.geofiber_backend.dto.box.BoxResponse;
import com.geofiber.geofiber_backend.dto.geo.CoordinateDto;
import com.geofiber.geofiber_backend.entities.box.BoxAssetEntity;
import com.geofiber.geofiber_backend.enums.InfrastructureStatusEnum;
import com.geofiber.geofiber_backend.enums.box.BoxTypeEnum;
import com.geofiber.geofiber_backend.repositories.box.BoxRepository;
import com.geofiber.geofiber_backend.repositories.serviceport.ServicePortRepository;
import com.geofiber.geofiber_backend.repositories.splitter.SplitterRepository;
import com.geofiber.geofiber_backend.repositories.tray.TrayRepository;
import com.geofiber.geofiber_backend.repositories.connection.OpticalConnectionRepository;
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
public class BoxService {
    private final BoxRepository boxRepository;
    private final ServicePortRepository servicePortRepository;
    private final SplitterRepository splitterRepository;
    private final TrayRepository trayRepository;
    private final OpticalConnectionRepository connectionRepository;
    private final CableAttachmentRepository attachmentRepository;

    private final OwnershipGuard ownership;

    public BoxService(
        BoxRepository boxRepository,
        ServicePortRepository servicePortRepository,
        SplitterRepository splitterRepository,
        TrayRepository trayRepository,
        OpticalConnectionRepository connectionRepository,
        CableAttachmentRepository attachmentRepository,
        OwnershipGuard ownership
    ) {
        this.ownership = ownership;
        this.boxRepository = boxRepository;
        this.servicePortRepository = servicePortRepository;
        this.splitterRepository = splitterRepository;
        this.trayRepository = trayRepository;
        this.connectionRepository = connectionRepository;
        this.attachmentRepository = attachmentRepository;
    }

    public BoxResponse create(BoxRequest request) {
        BoxAssetEntity box = new BoxAssetEntity();
        box.setCreatedBy(ownership.currentUserId());
        apply(box, request);
        return toResponse(boxRepository.saveAndFlush(box));
    }

    @Transactional(readOnly = true)
    public List<BoxResponse> findAll() {
        return boxRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BoxResponse findById(UUID id) {
        return toResponse(getBox(id));
    }

    public BoxResponse update(UUID id, BoxRequest request) {
        BoxAssetEntity box = getBox(id);
        ownership.requireOwner(box.getCreatedBy());
        if (box.getType() == BoxTypeEnum.CTO && request.type() != BoxTypeEnum.CTO
            && servicePortRepository.existsByBox_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Box has service ports and must remain a CTO");
        }
        int highestServicePort = servicePortRepository.findLastPortNumber(id);
        if (request.powerCapacity() != null && request.powerCapacity() < highestServicePort) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Box supports at most " + request.powerCapacity() + " service ports");
        }
        if (request.trayCapacity() != null && request.trayCapacity() < trayRepository.findHighestTrayNumber(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Box has trays numbered above the new tray capacity");
        }
        apply(box, request);
        return toResponse(boxRepository.saveAndFlush(box));
    }

    public void delete(UUID id) {
        BoxAssetEntity box = getBox(id);
        ownership.requireOwner(box.getCreatedBy());
        boolean inUse = splitterRepository.existsByBox_Id(id) || trayRepository.existsByBox_Id(id)
            || servicePortRepository.existsByBox_Id(id) || connectionRepository.existsByBox_Id(id)
            || attachmentRepository.existsByBox_Id(id);
        if (inUse) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Box still has splitters, trays, service ports, connections or attached cables");
        }
        boxRepository.delete(box);
    }

    private BoxAssetEntity getBox(UUID id) {
        return boxRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Box not found"));
    }

    private void apply(BoxAssetEntity box, BoxRequest request) {
        box.setName(request.name());
        box.setDescription(request.description());
        box.setType(request.type());
        box.setUsageStatus(request.usageStatus() == null
            ? InfrastructureStatusEnum.PLANNED : request.usageStatus());
        box.setPowerCapacity(request.powerCapacity());
        box.setTrayCapacity(request.trayCapacity());
        box.setManufacturer(request.manufacturer());
        box.setModel(request.model());
        box.setAddress(request.address());
        box.setLocation(GeometryMapper.toPoint(request.location()));
    }

    private BoxResponse toResponse(BoxAssetEntity box) {
        CoordinateDto location = GeometryMapper.fromPoint(box.getLocation());
        return new BoxResponse(
            box.getId(), box.getName(), box.getDescription(), box.getType(),
            box.getUsageStatus(), box.getPowerCapacity(), box.getTrayCapacity(),
            box.getManufacturer(), box.getModel(), box.getAddress(), location
        );
    }
}
