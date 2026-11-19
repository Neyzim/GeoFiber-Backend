package com.geofiber.geofiber_backend.services.tray;

import com.geofiber.geofiber_backend.dto.tray.TrayRequest;
import com.geofiber.geofiber_backend.dto.tray.TrayResponse;
import com.geofiber.geofiber_backend.entities.box.BoxAssetEntity;
import com.geofiber.geofiber_backend.entities.tray.TrayEntity;
import com.geofiber.geofiber_backend.repositories.box.BoxRepository;
import com.geofiber.geofiber_backend.repositories.splice.SpliceRepository;
import com.geofiber.geofiber_backend.repositories.tray.TrayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.geofiber.geofiber_backend.services.common.ServiceErrors.badRequest;
import static com.geofiber.geofiber_backend.services.common.ServiceErrors.conflict;
import static com.geofiber.geofiber_backend.services.common.ServiceErrors.notFound;

@Service
@Transactional
public class TrayService {
    private final BoxRepository boxRepository;
    private final TrayRepository trayRepository;
    private final SpliceRepository spliceRepository;

    public TrayService(BoxRepository boxRepository, TrayRepository trayRepository, SpliceRepository spliceRepository) {
        this.boxRepository = boxRepository;
        this.trayRepository = trayRepository;
        this.spliceRepository = spliceRepository;
    }

    public TrayResponse create(UUID boxId, TrayRequest request) {
        BoxAssetEntity box = boxRepository.findById(boxId).orElseThrow(notFound("Box"));
        TrayEntity tray = new TrayEntity();
        tray.setBox(box);
        return toResponse(trayRepository.saveAndFlush(apply(tray, request)));
    }

    public TrayResponse update(UUID boxId, UUID trayId, TrayRequest request) {
        TrayEntity tray = getTray(boxId, trayId);
        if (request.capacity() < spliceRepository.findLastPositionInTray(trayId)) {
            throw badRequest("Tray capacity is below its highest used position");
        }
        return toResponse(trayRepository.saveAndFlush(apply(tray, request)));
    }

    public void delete(UUID boxId, UUID trayId) {
        TrayEntity tray = getTray(boxId, trayId);
        if (spliceRepository.countByTray_Id(trayId) > 0) {
            throw conflict("Tray still has splices");
        }
        trayRepository.delete(tray);
    }

    @Transactional(readOnly = true)
    public List<TrayResponse> findByBox(UUID boxId) {
        if (!boxRepository.existsById(boxId)) {
            throw notFound("Box").get();
        }
        return trayRepository.findAllByBox_IdOrderByTrayNumberAsc(boxId).stream().map(this::toResponse).toList();
    }

    private static TrayEntity apply(TrayEntity tray, TrayRequest request) {
        Integer trayCapacity = tray.getBox().getTrayCapacity();
        if (trayCapacity != null && request.trayNumber() > trayCapacity) {
            throw badRequest("Box supports at most " + trayCapacity + " trays");
        }
        tray.setTrayNumber(request.trayNumber());
        tray.setCapacity(request.capacity());
        return tray;
    }

    private TrayEntity getTray(UUID boxId, UUID trayId) {
        TrayEntity tray = trayRepository.findById(trayId).orElseThrow(notFound("Tray"));
        if (!tray.getBox().getId().equals(boxId)) {
            throw notFound("Tray").get();
        }
        return tray;
    }

    private TrayResponse toResponse(TrayEntity tray) {
        return new TrayResponse(
            tray.getId(), tray.getBox().getId(), tray.getTrayNumber(), tray.getCapacity(),
            spliceRepository.countByTray_Id(tray.getId())
        );
    }
}
