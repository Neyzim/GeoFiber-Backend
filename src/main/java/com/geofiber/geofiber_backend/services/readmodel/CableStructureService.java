package com.geofiber.geofiber_backend.services.readmodel;

import com.geofiber.geofiber_backend.dto.cable.FiberRequest;
import com.geofiber.geofiber_backend.dto.cable.FiberResponse;
import com.geofiber.geofiber_backend.dto.cable.LooseTubeRequest;
import com.geofiber.geofiber_backend.dto.cable.LooseTubeResponse;
import com.geofiber.geofiber_backend.dto.readmodel.CableStructureRequest;
import com.geofiber.geofiber_backend.dto.readmodel.CableStructureResponse;
import com.geofiber.geofiber_backend.enums.OpticalColorEnum;
import com.geofiber.geofiber_backend.repositories.cable.CableRepository;
import com.geofiber.geofiber_backend.repositories.cable.FiberRepository;
import com.geofiber.geofiber_backend.services.cable.FiberService;
import com.geofiber.geofiber_backend.services.cable.LooseTubeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.geofiber.geofiber_backend.services.common.ServiceErrors.notFound;

/**
 * Cria tubos e fibras de um cabo numa única transação. Cada tubo é preenchido até a capacidade,
 * com fibras nas cores ABNT (a sequência recomeça a cada 12) e numeração absoluta contínua após
 * as fibras já existentes. Qualquer regra violada desfaz tudo.
 */
@Service
@Transactional
public class CableStructureService {
    private static final OpticalColorEnum[] FIBER_COLORS = OpticalColorEnum.values();

    private final CableRepository cableRepository;
    private final FiberRepository fiberRepository;
    private final LooseTubeService looseTubeService;
    private final FiberService fiberService;

    public CableStructureService(
        CableRepository cableRepository, FiberRepository fiberRepository,
        LooseTubeService looseTubeService, FiberService fiberService
    ) {
        this.cableRepository = cableRepository;
        this.fiberRepository = fiberRepository;
        this.looseTubeService = looseTubeService;
        this.fiberService = fiberService;
    }

    public CableStructureResponse create(UUID cableId, CableStructureRequest request) {
        if (!cableRepository.existsById(cableId)) {
            throw notFound("Cable").get();
        }
        int absoluteNumber = fiberRepository.findHighestAbsoluteNumber(cableId);
        List<CableStructureResponse.Tube> tubes = new ArrayList<>();
        for (LooseTubeRequest tubeRequest : request.tubes()) {
            LooseTubeResponse tube = looseTubeService.create(cableId, tubeRequest);
            List<FiberResponse> fibers = new ArrayList<>();
            for (int number = 1; number <= tube.capacity(); number++) {
                fibers.add(fiberService.create(tube.id(), new FiberRequest(
                    number, ++absoluteNumber, FIBER_COLORS[(number - 1) % FIBER_COLORS.length])));
            }
            tubes.add(new CableStructureResponse.Tube(
                tube.id(), tube.tubeNumber(), tube.capacity(), tube.color(), fibers));
        }
        return new CableStructureResponse(cableId, tubes);
    }
}
