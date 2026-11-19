package com.geofiber.geofiber_backend.services.serviceport;

import com.geofiber.geofiber_backend.dto.serviceport.ServicePortRequest;
import com.geofiber.geofiber_backend.dto.serviceport.ServicePortResponse;
import com.geofiber.geofiber_backend.entities.box.BoxAssetEntity;
import com.geofiber.geofiber_backend.entities.customer.CustomerEntity;
import com.geofiber.geofiber_backend.entities.serviceport.ServicePortEntity;
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

import static com.geofiber.geofiber_backend.services.common.ServiceErrors.badRequest;
import static com.geofiber.geofiber_backend.services.common.ServiceErrors.conflict;
import static com.geofiber.geofiber_backend.services.common.ServiceErrors.notFound;

/**
 * Portas de atendimento das CTOs. A quantidade máxima por caixa é {@code powerCapacity}; os
 * números das portas vão de 1 até esse limite, como na etiqueta da caixa.
 */
@Service
@Transactional
public class ServicePortService {
    private final BoxRepository boxRepository;
    private final ServicePortRepository servicePortRepository;
    private final SplitterPortRepository splitterPortRepository;
    private final CustomerRepository customerRepository;
    private final FiberTerminationRepository terminationRepository;
    private final FiberTerminationService terminationService;
    private final CableAttachmentService cableAttachmentService;

    public ServicePortService(
        BoxRepository boxRepository,
        ServicePortRepository servicePortRepository,
        SplitterPortRepository splitterPortRepository,
        CustomerRepository customerRepository,
        FiberTerminationRepository terminationRepository,
        FiberTerminationService terminationService,
        CableAttachmentService cableAttachmentService
    ) {
        this.boxRepository = boxRepository;
        this.servicePortRepository = servicePortRepository;
        this.splitterPortRepository = splitterPortRepository;
        this.customerRepository = customerRepository;
        this.terminationRepository = terminationRepository;
        this.terminationService = terminationService;
        this.cableAttachmentService = cableAttachmentService;
    }

    public ServicePortResponse create(UUID boxId, ServicePortRequest request) {
        BoxAssetEntity box = boxRepository.findById(boxId).orElseThrow(notFound("Box"));
        requireCto(box);
        ServicePortEntity port = new ServicePortEntity();
        port.setBox(box);
        return apply(port, null, null, request);
    }

    public ServicePortResponse update(UUID boxId, UUID portId, ServicePortRequest request) {
        ServicePortEntity port = getPort(boxId, portId);
        return apply(port, terminationRepository.findByServicePort_Id(portId).orElse(null),
            customerRepository.findByServicePort_Id(portId).orElse(null), request);
    }

    public void delete(UUID boxId, UUID portId) {
        ServicePortEntity port = getPort(boxId, portId);
        if (customerRepository.findByServicePort_Id(portId).isPresent()) {
            throw conflict("Service port is serving a customer");
        }
        terminationRepository.findByServicePort_Id(portId).ifPresent(terminationService::release);
        freeSplitterPort(port.getSplitterPort());
        terminationRepository.flush();
        servicePortRepository.delete(port);
    }

    @Transactional(readOnly = true)
    public List<ServicePortResponse> findByBox(UUID boxId) {
        if (!boxRepository.existsById(boxId)) {
            throw notFound("Box").get();
        }
        List<ServicePortEntity> ports = servicePortRepository.findAllByBox_IdOrderByPortNumberAsc(boxId);
        List<UUID> ids = ports.stream().map(ServicePortEntity::getId).toList();
        Map<UUID, FiberTerminationEntity> terminations = terminationRepository.findAllByServicePort_IdIn(ids).stream()
            .collect(Collectors.toMap(t -> t.getServicePort().getId(), Function.identity()));
        Map<UUID, CustomerEntity> customers = customerRepository.findAllByServicePort_IdIn(ids).stream()
            .collect(Collectors.toMap(c -> c.getServicePort().getId(), Function.identity()));
        return ports.stream()
            .map(port -> toResponse(port, terminations.get(port.getId()), customers.get(port.getId())))
            .toList();
    }

    /** Cria uma porta de atendimento para cada saída, numeradas após a última porta da caixa. */
    public void createForSplitterOutputs(BoxAssetEntity box, List<SplitterPortEntity> outputs) {
        int next = servicePortRepository.findLastPortNumber(box.getId()) + 1;
        requireCapacity(box, next + outputs.size() - 1);
        for (SplitterPortEntity output : outputs) {
            ServicePortEntity port = new ServicePortEntity();
            port.setBox(box);
            port.setPortNumber(next++);
            port.setSplitterPort(output);
            output.setStatus(PortStatusEnum.OCCUPIED);
            servicePortRepository.save(port);
        }
    }

    public static void requireCto(BoxAssetEntity box) {
        if (box.getType() != BoxTypeEnum.CTO) {
            throw badRequest("Only CTO boxes have service ports");
        }
    }

    private ServicePortResponse apply(
        ServicePortEntity port, FiberTerminationEntity currentTermination, CustomerEntity customer,
        ServicePortRequest request
    ) {
        if (request.splitterPortId() != null && request.fiberId() != null) {
            throw badRequest("A service port takes either a splitter port or a fiber, not both");
        }
        if (customer != null && request.splitterPortId() == null && request.fiberId() == null) {
            throw conflict("Service port is serving a customer");
        }
        requireCapacity(port.getBox(), request.portNumber());
        port.setPortNumber(request.portNumber());
        port.setLabel(request.label());

        SplitterPortEntity splitterPort = request.splitterPortId() == null ? null
            : getFeedingSplitterPort(port, request.splitterPortId());
        if (request.fiberId() == null && currentTermination != null) {
            // O trigger de fonte única exige que a fibra saia antes de a saída do splitter entrar.
            terminationService.relink(currentTermination, null, FiberEndEnum.B, termination -> { });
            currentTermination = null;
        }
        if (port.getSplitterPort() != null && port.getSplitterPort() != splitterPort) {
            freeSplitterPort(port.getSplitterPort());
        }
        port.setSplitterPort(splitterPort);
        if (splitterPort != null) {
            splitterPort.setStatus(PortStatusEnum.OCCUPIED);
        }
        servicePortRepository.save(port);

        if (request.fiberId() != null) {
            cableAttachmentService.requireFiberAttachedToBox(request.fiberId(), port.getBox().getId());
        }
        FiberTerminationEntity linked = terminationService.relink(
            currentTermination, request.fiberId(),
            request.fiberEnd() == null ? FiberEndEnum.B : request.fiberEnd(),
            termination -> termination.setServicePort(port));

        port.setStatus(PortStatusResolver.resolve(request.status(), customer != null));
        servicePortRepository.flush();
        return toResponse(port, linked, customer);
    }

    private SplitterPortEntity getFeedingSplitterPort(ServicePortEntity servicePort, UUID splitterPortId) {
        SplitterPortEntity splitterPort = splitterPortRepository.findById(splitterPortId)
            .orElseThrow(notFound("Splitter port"));
        if (splitterPort.getDirection() != PortDirectionEnum.OUTPUT
            || !splitterPort.getSplitter().getBox().getId().equals(servicePort.getBox().getId())) {
            throw badRequest("Splitter port must be an output of a splitter in this box");
        }
        if (terminationRepository.findBySplitterPort_Id(splitterPortId).isPresent()) {
            throw conflict("Splitter port already feeds a fiber");
        }
        servicePortRepository.findBySplitterPort_Id(splitterPortId)
            .filter(other -> !other.getId().equals(servicePort.getId()))
            .ifPresent(other -> {
                throw conflict("Splitter port already feeds a service port");
            });
        return splitterPort;
    }

    private static void freeSplitterPort(SplitterPortEntity splitterPort) {
        if (splitterPort != null && splitterPort.getStatus() == PortStatusEnum.OCCUPIED) {
            splitterPort.setStatus(PortStatusEnum.FREE);
        }
    }

    private static void requireCapacity(BoxAssetEntity box, int highestPortNumber) {
        if (box.getPowerCapacity() != null && highestPortNumber > box.getPowerCapacity()) {
            throw badRequest("Box supports at most " + box.getPowerCapacity() + " service ports");
        }
    }

    private ServicePortEntity getPort(UUID boxId, UUID portId) {
        ServicePortEntity port = servicePortRepository.findById(portId).orElseThrow(notFound("Service port"));
        if (!port.getBox().getId().equals(boxId)) {
            throw notFound("Service port").get();
        }
        return port;
    }

    private ServicePortResponse toResponse(
        ServicePortEntity port, FiberTerminationEntity termination, CustomerEntity customer
    ) {
        return new ServicePortResponse(
            port.getId(), port.getBox().getId(), port.getPortNumber(), port.getLabel(), port.getStatus(),
            port.getSplitterPort() == null ? null : port.getSplitterPort().getId(),
            termination == null ? null : termination.getFiber().getId(),
            termination == null ? null : termination.getFiberEnd(),
            customer == null ? null : customer.getId());
    }
}
