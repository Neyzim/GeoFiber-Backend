package com.geofiber.geofiber_backend.services.splitter;

import com.geofiber.geofiber_backend.dto.splitter.SplitterPortResponse;
import com.geofiber.geofiber_backend.dto.splitter.SplitterPortUpdateRequest;
import com.geofiber.geofiber_backend.dto.splitter.SplitterRequest;
import com.geofiber.geofiber_backend.dto.splitter.SplitterResponse;
import com.geofiber.geofiber_backend.entities.box.BoxAssetEntity;
import com.geofiber.geofiber_backend.entities.serviceport.ServicePortEntity;
import com.geofiber.geofiber_backend.entities.splitter.SplitterEntity;
import com.geofiber.geofiber_backend.entities.splitter.SplitterPortEntity;
import com.geofiber.geofiber_backend.entities.termination.FiberTerminationEntity;
import com.geofiber.geofiber_backend.enums.FiberEndEnum;
import com.geofiber.geofiber_backend.enums.PortDirectionEnum;
import com.geofiber.geofiber_backend.enums.PortStatusEnum;
import com.geofiber.geofiber_backend.enums.box.BoxTypeEnum;
import com.geofiber.geofiber_backend.repositories.box.BoxRepository;
import com.geofiber.geofiber_backend.repositories.customer.CustomerRepository;
import com.geofiber.geofiber_backend.repositories.serviceport.ServicePortRepository;
import com.geofiber.geofiber_backend.repositories.splitter.SplitterPortRepository;
import com.geofiber.geofiber_backend.repositories.splitter.SplitterRepository;
import com.geofiber.geofiber_backend.repositories.termination.FiberTerminationRepository;
import com.geofiber.geofiber_backend.services.cable.CableAttachmentService;
import com.geofiber.geofiber_backend.services.common.PortStatusResolver;
import com.geofiber.geofiber_backend.services.serviceport.ServicePortService;
import com.geofiber.geofiber_backend.services.termination.FiberTerminationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.geofiber.geofiber_backend.services.common.ServiceErrors.badRequest;
import static com.geofiber.geofiber_backend.services.common.ServiceErrors.conflict;
import static com.geofiber.geofiber_backend.services.common.ServiceErrors.notFound;

@Service
@Transactional
public class SplitterService {
    private final BoxRepository boxRepository;
    private final SplitterRepository splitterRepository;
    private final SplitterPortRepository splitterPortRepository;
    private final ServicePortRepository servicePortRepository;
    private final FiberTerminationRepository terminationRepository;
    private final FiberTerminationService terminationService;
    private final CableAttachmentService cableAttachmentService;
    private final ServicePortService servicePortService;
    private final CustomerRepository customerRepository;

    public SplitterService(
        BoxRepository boxRepository,
        SplitterRepository splitterRepository,
        SplitterPortRepository splitterPortRepository,
        ServicePortRepository servicePortRepository,
        FiberTerminationRepository terminationRepository,
        FiberTerminationService terminationService,
        CableAttachmentService cableAttachmentService,
        ServicePortService servicePortService,
        CustomerRepository customerRepository
    ) {
        this.boxRepository = boxRepository;
        this.splitterRepository = splitterRepository;
        this.splitterPortRepository = splitterPortRepository;
        this.servicePortRepository = servicePortRepository;
        this.terminationRepository = terminationRepository;
        this.terminationService = terminationService;
        this.cableAttachmentService = cableAttachmentService;
        this.servicePortService = servicePortService;
        this.customerRepository = customerRepository;
    }

    /**
     * Cria o splitter com a porta de entrada (número 1) e as portas de saída (2..N+1). Numa CTO,
     * por padrão gera uma porta de atendimento por saída.
     */
    public SplitterResponse create(UUID boxId, SplitterRequest request) {
        BoxAssetEntity box = boxRepository.findById(boxId).orElseThrow(notFound("Box"));
        boolean createServicePorts = request.createServicePorts() == null
            ? box.getType() == BoxTypeEnum.CTO : request.createServicePorts();
        if (createServicePorts) {
            ServicePortService.requireCto(box);
        }

        SplitterEntity splitter = new SplitterEntity();
        splitter.setBox(box);
        splitter.setRatio(request.ratio());
        splitter.setBalanced(request.balanced());
        splitterRepository.save(splitter);

        List<SplitterPortEntity> ports = new ArrayList<>();
        ports.add(newPort(splitter, 1, PortDirectionEnum.INPUT));
        for (int output = 1; output <= request.ratio().getOutputCount(); output++) {
            ports.add(newPort(splitter, output + 1, PortDirectionEnum.OUTPUT));
        }
        splitterPortRepository.saveAll(ports);
        if (createServicePorts) {
            servicePortService.createForSplitterOutputs(box, ports.subList(1, ports.size()));
        }
        splitterPortRepository.flush();
        return toResponse(splitter, ports);
    }

    @Transactional(readOnly = true)
    public List<SplitterResponse> findByBox(UUID boxId) {
        if (!boxRepository.existsById(boxId)) {
            throw notFound("Box").get();
        }
        return splitterRepository.findAllByBox_Id(boxId).stream()
            .map(splitter -> toResponse(splitter,
                splitterPortRepository.findAllBySplitter_IdOrderByPortNumberAsc(splitter.getId())))
            .toList();
    }

    public SplitterPortResponse updatePort(UUID splitterId, UUID portId, SplitterPortUpdateRequest request) {
        SplitterPortEntity port = splitterPortRepository.findById(portId).orElseThrow(notFound("Splitter port"));
        if (!port.getSplitter().getId().equals(splitterId)) {
            throw notFound("Splitter port").get();
        }
        ServicePortEntity servicePort = servicePortRepository.findBySplitterPort_Id(portId).orElse(null);
        if (servicePort != null && request.fiberId() != null) {
            throw conflict("Splitter port already feeds a service port");
        }

        if (request.fiberId() != null) {
            cableAttachmentService.requireFiberAttachedToBox(request.fiberId(), port.getSplitter().getBox().getId());
        }
        FiberEndEnum defaultEnd = port.getDirection() == PortDirectionEnum.INPUT ? FiberEndEnum.B : FiberEndEnum.A;
        FiberTerminationEntity linked = terminationService.relink(
            terminationRepository.findBySplitterPort_Id(portId).orElse(null),
            request.fiberId(),
            request.fiberEnd() == null ? defaultEnd : request.fiberEnd(),
            termination -> termination.setSplitterPort(port));

        port.setAttenuationDb(request.attenuationDb());
        port.setStatus(PortStatusResolver.resolve(request.status(), linked != null || servicePort != null));
        splitterPortRepository.flush();
        return toResponse(port, linked, servicePort);
    }

    /** Só {@code balanced} pode mudar; a razão define as portas e é imutável. */
    public SplitterResponse update(UUID boxId, UUID splitterId, SplitterRequest request) {
        SplitterEntity splitter = getSplitter(boxId, splitterId);
        if (request.ratio() != splitter.getRatio()) {
            throw badRequest("Splitter ratio cannot be changed");
        }
        splitter.setBalanced(request.balanced());
        splitterRepository.flush();
        return toResponse(splitter, splitterPortRepository.findAllBySplitter_IdOrderByPortNumberAsc(splitterId));
    }

    /**
     * Exclui o splitter, suas portas e as portas de atendimento que ele alimenta. Bloqueado se
     * alguma porta tiver fibra ou alimentar uma porta de atendimento com cliente.
     */
    public void delete(UUID boxId, UUID splitterId) {
        SplitterEntity splitter = getSplitter(boxId, splitterId);
        List<SplitterPortEntity> ports = splitterPortRepository.findAllBySplitter_Id(splitterId);
        List<UUID> portIds = ports.stream().map(SplitterPortEntity::getId).toList();
        List<ServicePortEntity> servicePorts = servicePortRepository.findAllBySplitterPort_IdIn(portIds);
        boolean connected = !terminationRepository.findAllBySplitterPort_IdIn(portIds).isEmpty()
            || !customerRepository.findAllByServicePort_IdIn(
                servicePorts.stream().map(ServicePortEntity::getId).toList()).isEmpty();
        if (connected) {
            throw conflict("Splitter still has connections");
        }
        servicePortRepository.deleteAll(servicePorts);
        splitterPortRepository.deleteAll(ports);
        splitterRepository.delete(splitter);
    }

    private SplitterEntity getSplitter(UUID boxId, UUID splitterId) {
        SplitterEntity splitter = splitterRepository.findById(splitterId).orElseThrow(notFound("Splitter"));
        if (!splitter.getBox().getId().equals(boxId)) {
            throw notFound("Splitter").get();
        }
        return splitter;
    }

    private SplitterPortEntity newPort(SplitterEntity splitter, int number, PortDirectionEnum direction) {
        SplitterPortEntity port = new SplitterPortEntity();
        port.setSplitter(splitter);
        port.setPortNumber(number);
        port.setDirection(direction);
        port.setStatus(PortStatusEnum.FREE);
        return port;
    }

    private SplitterResponse toResponse(SplitterEntity splitter, List<SplitterPortEntity> ports) {
        List<UUID> portIds = ports.stream().map(SplitterPortEntity::getId).toList();
        Map<UUID, FiberTerminationEntity> terminations = terminationRepository.findAllBySplitterPort_IdIn(portIds)
            .stream().collect(Collectors.toMap(t -> t.getSplitterPort().getId(), Function.identity()));
        Map<UUID, ServicePortEntity> servicePorts = servicePortRepository.findAllBySplitterPort_IdIn(portIds)
            .stream().collect(Collectors.toMap(p -> p.getSplitterPort().getId(), Function.identity()));
        List<SplitterPortResponse> portResponses = ports.stream()
            .map(port -> toResponse(port, terminations.get(port.getId()), servicePorts.get(port.getId())))
            .toList();
        return new SplitterResponse(
            splitter.getId(), splitter.getBox().getId(), splitter.getRatio(), splitter.isBalanced(), portResponses
        );
    }

    private SplitterPortResponse toResponse(
        SplitterPortEntity port, FiberTerminationEntity termination, ServicePortEntity servicePort
    ) {
        return new SplitterPortResponse(
            port.getId(), port.getSplitter().getId(), port.getPortNumber(), port.getAttenuationDb(),
            port.getStatus(), port.getDirection(),
            termination == null ? null : termination.getFiber().getId(),
            termination == null ? null : termination.getFiberEnd(),
            servicePort == null ? null : servicePort.getId()
        );
    }
}
