package com.geofiber.geofiber_backend.services.splice;

import com.geofiber.geofiber_backend.dto.splice.SpliceRequest;
import com.geofiber.geofiber_backend.dto.splice.SpliceResponse;
import com.geofiber.geofiber_backend.entities.box.BoxAssetEntity;
import com.geofiber.geofiber_backend.entities.connection.OpticalConnectionEntity;
import com.geofiber.geofiber_backend.entities.splice.SpliceEntity;
import com.geofiber.geofiber_backend.entities.termination.FiberTerminationEntity;
import com.geofiber.geofiber_backend.entities.tray.TrayEntity;
import com.geofiber.geofiber_backend.enums.FiberEndEnum;
import com.geofiber.geofiber_backend.enums.OpticalConnectionTypeEnum;
import com.geofiber.geofiber_backend.repositories.box.BoxRepository;
import com.geofiber.geofiber_backend.repositories.connection.OpticalConnectionRepository;
import com.geofiber.geofiber_backend.repositories.splice.SpliceRepository;
import com.geofiber.geofiber_backend.repositories.tray.TrayRepository;
import com.geofiber.geofiber_backend.services.cable.CableAttachmentService;
import com.geofiber.geofiber_backend.services.termination.FiberTerminationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.geofiber.geofiber_backend.services.common.ServiceErrors.badRequest;
import static com.geofiber.geofiber_backend.services.common.ServiceErrors.conflict;
import static com.geofiber.geofiber_backend.services.common.ServiceErrors.notFound;

/**
 * Uma emenda (fusão) liga uma ponta da fibra de entrada a uma ponta da fibra de saída,
 * ocupando uma posição de uma bandeja da caixa.
 */
@Service
@Transactional
public class SpliceService {
    private final BoxRepository boxRepository;
    private final TrayRepository trayRepository;
    private final SpliceRepository spliceRepository;
    private final OpticalConnectionRepository connectionRepository;
    private final FiberTerminationService terminationService;
    private final CableAttachmentService cableAttachmentService;

    public SpliceService(
        BoxRepository boxRepository,
        TrayRepository trayRepository,
        SpliceRepository spliceRepository,
        OpticalConnectionRepository connectionRepository,
        FiberTerminationService terminationService,
        CableAttachmentService cableAttachmentService
    ) {
        this.boxRepository = boxRepository;
        this.trayRepository = trayRepository;
        this.spliceRepository = spliceRepository;
        this.connectionRepository = connectionRepository;
        this.terminationService = terminationService;
        this.cableAttachmentService = cableAttachmentService;
    }

    public SpliceResponse create(UUID boxId, SpliceRequest request) {
        if (request.inputFiberId().equals(request.outputFiberId())) {
            throw badRequest("Input and output fibers must be different");
        }
        BoxAssetEntity box = boxRepository.findById(boxId).orElseThrow(notFound("Box"));
        TrayEntity tray = trayRepository.findByBox_IdAndTrayNumber(boxId, request.trayNumber())
            .orElseThrow(notFound("Tray " + request.trayNumber() + " of this box"));

        Integer position;
        if (request.positionNumber() != null) {
            // posição fornecida: validar range e disponibilidade
            if (request.positionNumber() > tray.getCapacity()) {
                throw badRequest("Position " + request.positionNumber() + " exceeds tray capacity of " + tray.getCapacity());
            }
            if (spliceRepository.existsByTray_IdAndPositionNumber(tray.getId(), request.positionNumber())) {
                throw conflict("Position " + request.positionNumber() + " is already occupied in this tray");
            }
            position = request.positionNumber();
        } else {
            // próxima livre
            position = spliceRepository.findFirstFreePosition(tray.getId(), tray.getCapacity());
            if (position == null) {
                throw conflict("Tray " + tray.getTrayNumber() + " is full");
            }
        }

        cableAttachmentService.requireFiberAttachedToBox(request.inputFiberId(), boxId);
        cableAttachmentService.requireFiberAttachedToBox(request.outputFiberId(), boxId);

        FiberTerminationEntity input = terminationService.getAvailable(request.inputFiberId(),
            request.inputFiberEnd() == null ? FiberEndEnum.B : request.inputFiberEnd());
        FiberTerminationEntity output = terminationService.getAvailable(request.outputFiberId(),
            request.outputFiberEnd() == null ? FiberEndEnum.A : request.outputFiberEnd());

        SpliceEntity splice = new SpliceEntity();
        splice.setBox(box);
        splice.setTray(tray);
        splice.setPositionNumber(position);
        splice.setAttenuationDb(request.attenuationDb());
        splice.setInputFiber(input.getFiber());
        splice.setOutputFiber(output.getFiber());
        splice.setConnection(terminationService.connect(
            input, output, OpticalConnectionTypeEnum.FUSION, box, request.attenuationDb()));
        return toResponse(spliceRepository.saveAndFlush(splice));
    }

    @Transactional(readOnly = true)
    public List<SpliceResponse> findByBox(UUID boxId) {
        if (!boxRepository.existsById(boxId)) {
            throw notFound("Box").get();
        }
        return spliceRepository.findAllByBox_IdOrderByTray_TrayNumberAscPositionNumberAsc(boxId)
            .stream().map(this::toResponse).toList();
    }

    /** Desfaz a fusão e libera as duas pontas de fibra. */
    public void delete(UUID boxId, UUID spliceId) {
        SpliceEntity splice = spliceRepository.findById(spliceId).orElseThrow(notFound("Splice"));
        if (!splice.getBox().getId().equals(boxId)) {
            throw notFound("Splice").get();
        }
        OpticalConnectionEntity connection = splice.getConnection();
        terminationService.release(connection.getTerminationA());
        terminationService.release(connection.getTerminationB());
        spliceRepository.delete(splice);
        connectionRepository.delete(connection);
        spliceRepository.flush();
    }

    private SpliceResponse toResponse(SpliceEntity splice) {
        OpticalConnectionEntity connection = splice.getConnection();
        UUID inputFiberId = splice.getInputFiber().getId();
        FiberTerminationEntity inputEnd = connection.getTerminationA().getFiber().getId().equals(inputFiberId)
            ? connection.getTerminationA() : connection.getTerminationB();
        FiberTerminationEntity outputEnd = inputEnd == connection.getTerminationA()
            ? connection.getTerminationB() : connection.getTerminationA();
        return new SpliceResponse(
            splice.getId(), splice.getBox().getId(), splice.getTray().getTrayNumber(), splice.getPositionNumber(),
            splice.getAttenuationDb(),
            inputFiberId, inputEnd.getFiberEnd(),
            splice.getOutputFiber().getId(), outputEnd.getFiberEnd()
        );
    }
}
