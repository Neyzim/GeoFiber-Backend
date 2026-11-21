package com.geofiber.geofiber_backend.services.trace;

import com.geofiber.geofiber_backend.dto.geo.CoordinateDto;
import com.geofiber.geofiber_backend.dto.trace.TraceResponse.FiberInfo;
import com.geofiber.geofiber_backend.dto.trace.TraceResponse.TraceItem;
import com.geofiber.geofiber_backend.dto.trace.TraceResponse.TracePosition;
import com.geofiber.geofiber_backend.dto.trace.TraceResponse.TraceStep;
import com.geofiber.geofiber_backend.entities.box.BoxAssetEntity;
import com.geofiber.geofiber_backend.entities.cable.CableAssetEntity;
import com.geofiber.geofiber_backend.entities.cable.CableAttachmentEntity;
import com.geofiber.geofiber_backend.entities.cable.FiberEntity;
import com.geofiber.geofiber_backend.entities.connection.OpticalConnectionEntity;
import com.geofiber.geofiber_backend.entities.customer.CustomerEntity;
import com.geofiber.geofiber_backend.entities.olt.OltPortEntity;
import com.geofiber.geofiber_backend.entities.serviceport.ServicePortEntity;
import com.geofiber.geofiber_backend.entities.splice.SpliceEntity;
import com.geofiber.geofiber_backend.entities.splitter.SplitterEntity;
import com.geofiber.geofiber_backend.entities.splitter.SplitterPortEntity;
import com.geofiber.geofiber_backend.entities.termination.FiberTerminationEntity;
import com.geofiber.geofiber_backend.enums.FiberEndEnum;
import com.geofiber.geofiber_backend.enums.PortDirectionEnum;
import com.geofiber.geofiber_backend.enums.trace.TraceItemKindEnum;
import com.geofiber.geofiber_backend.repositories.cable.CableAttachmentRepository;
import com.geofiber.geofiber_backend.repositories.cable.FiberRepository;
import com.geofiber.geofiber_backend.repositories.connection.OpticalConnectionRepository;
import com.geofiber.geofiber_backend.repositories.customer.CustomerRepository;
import com.geofiber.geofiber_backend.repositories.serviceport.ServicePortRepository;
import com.geofiber.geofiber_backend.repositories.splice.SpliceRepository;
import com.geofiber.geofiber_backend.repositories.splitter.SplitterPortRepository;
import com.geofiber.geofiber_backend.repositories.splitter.SplitterRepository;
import com.geofiber.geofiber_backend.repositories.termination.FiberTerminationRepository;
import com.geofiber.geofiber_backend.services.geo.GeometryMapper;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static com.geofiber.geofiber_backend.services.common.ServiceErrors.notFound;

/**
 * Consultas da rede usadas pelo rastreio, mais a montagem de rótulos, posições e passos. Só leitura.
 */
final class TraceNetwork {
    private final FiberRepository fibers;
    private final FiberTerminationRepository terminations;
    private final OpticalConnectionRepository connections;
    private final SpliceRepository splices;
    private final SplitterRepository splitters;
    private final SplitterPortRepository splitterPorts;
    private final ServicePortRepository servicePorts;
    private final CustomerRepository customers;
    private final CableAttachmentRepository attachments;

    TraceNetwork(
        FiberRepository fibers, FiberTerminationRepository terminations, OpticalConnectionRepository connections,
        SpliceRepository splices, SplitterRepository splitters, SplitterPortRepository splitterPorts,
        ServicePortRepository servicePorts, CustomerRepository customers, CableAttachmentRepository attachments
    ) {
        this.fibers = fibers;
        this.terminations = terminations;
        this.connections = connections;
        this.splices = splices;
        this.splitters = splitters;
        this.splitterPorts = splitterPorts;
        this.servicePorts = servicePorts;
        this.customers = customers;
        this.attachments = attachments;
    }

    // ---------- consultas ----------

    FiberEntity fiber(UUID id) {
        return fibers.findById(id).orElseThrow(notFound("Fiber"));
    }

    FiberTerminationEntity termination(UUID id) {
        return terminations.findById(id).orElseThrow(notFound("Fiber end"));
    }

    SpliceEntity splice(UUID id) {
        return splices.findById(id).orElseThrow(notFound("Splice"));
    }

    SplitterEntity splitter(UUID id) {
        return splitters.findById(id).orElseThrow(notFound("Splitter"));
    }

    SplitterPortEntity splitterPort(UUID id) {
        return splitterPorts.findById(id).orElseThrow(notFound("Splitter port"));
    }

    FiberTerminationEntity end(FiberEntity fiber, FiberEndEnum end) {
        return terminations.findByFiber_IdAndFiberEnd(fiber.getId(), end).orElseThrow(notFound("Fiber end"));
    }

    FiberTerminationEntity otherEnd(FiberTerminationEntity termination) {
        FiberEndEnum other = termination.getFiberEnd() == FiberEndEnum.A ? FiberEndEnum.B : FiberEndEnum.A;
        return end(termination.getFiber(), other);
    }

    /** Ponta de fibra ligada à porta de splitter, ou nulo. */
    FiberTerminationEntity terminationOf(SplitterPortEntity port) {
        return terminations.findBySplitterPort_Id(port.getId()).orElse(null);
    }

    /** Ponta de fibra que alimenta a porta de atendimento direto (sem splitter), ou nulo. */
    FiberTerminationEntity terminationOf(ServicePortEntity port) {
        return terminations.findByServicePort_Id(port.getId()).orElse(null);
    }

    /** Porta de atendimento alimentada pela saída de splitter, ou nulo. */
    ServicePortEntity servicePortOf(SplitterPortEntity port) {
        return servicePorts.findBySplitterPort_Id(port.getId()).orElse(null);
    }

    /** Conexão óptica (fusão ou conector) que usa a ponta, ou nulo. */
    OpticalConnectionEntity connectionOf(FiberTerminationEntity termination) {
        return connections.findFirstByTerminationA_IdOrTerminationB_Id(termination.getId(), termination.getId())
            .orElse(null);
    }

    OpticalConnectionEntity firstConnectionOf(BoxAssetEntity box) {
        return connections.findFirstByBox_IdOrderByIdAsc(box.getId()).orElse(null);
    }

    List<SplitterPortEntity> portsOf(SplitterEntity splitter) {
        return splitterPorts.findAllBySplitter_IdOrderByPortNumberAsc(splitter.getId());
    }

    SplitterPortEntity inputOf(SplitterEntity splitter) {
        return portsOf(splitter).stream()
            .filter(port -> port.getDirection() == PortDirectionEnum.INPUT)
            .findFirst().orElse(null);
    }

    List<SplitterPortEntity> outputsOf(SplitterEntity splitter) {
        return portsOf(splitter).stream()
            .filter(port -> port.getDirection() == PortDirectionEnum.OUTPUT)
            .toList();
    }

    /** A entrada do splitter tem fibra ligada: sem isso o splitter não recebe sinal. */
    boolean hasInputFiber(SplitterEntity splitter) {
        SplitterPortEntity input = inputOf(splitter);
        return input != null && terminationOf(input) != null;
    }

    List<SplitterEntity> splittersOf(BoxAssetEntity box) {
        return splitters.findAllByBox_Id(box.getId()).stream()
            .sorted(Comparator.comparing(SplitterEntity::getId))
            .toList();
    }

    CustomerEntity customerOf(ServicePortEntity port) {
        return customers.findByServicePort_Id(port.getId()).orElse(null);
    }

    // ---------- rótulos ----------

    String fiberEndLabel(FiberTerminationEntity termination) {
        FiberEntity fiber = termination.getFiber();
        return fiberLabel(fiber) + " · ponta " + termination.getFiberEnd().name();
    }

    String fiberLabel(FiberEntity fiber) {
        return fiber.getCable().getName() + " · fibra " + fiber.getAbsoluteNumber();
    }

    String splitterLabel(SplitterEntity splitter) {
        return splitter.getBox().getName() + " · splitter 1:" + splitter.getRatio().getOutputCount();
    }

    String splitterPortLabel(SplitterPortEntity port) {
        String side = port.getDirection() == PortDirectionEnum.INPUT
            ? "entrada" : "saída " + (port.getPortNumber() - 1);
        return splitterLabel(port.getSplitter()) + " · " + side;
    }

    String servicePortLabel(ServicePortEntity port) {
        return port.getLabel() != null ? port.getLabel()
            : port.getBox().getName() + " · porta " + port.getPortNumber();
    }

    String spliceLabel(SpliceEntity splice) {
        return splice.getBox().getName() + " · bandeja " + splice.getTray().getTrayNumber()
            + " · posição " + splice.getPositionNumber();
    }

    // ---------- posições ----------

    /**
     * Posição da ponta para o mapa: o equipamento onde ela está ligada. Ponta sem ligação usa o
     * elemento de início (A) ou de fim (B) do cabo, quando o cabo tem esse elemento.
     */
    List<CoordinateDto> positionOf(FiberTerminationEntity termination) {
        OpticalConnectionEntity connection = connectionOf(termination);
        if (connection != null) {
            return single(boxLocation(connection.getBox()));
        }
        if (termination.getSplitterPort() != null) {
            return single(boxLocation(termination.getSplitterPort().getSplitter().getBox()));
        }
        if (termination.getServicePort() != null) {
            return single(boxLocation(termination.getServicePort().getBox()));
        }
        if (termination.getOltPort() != null) {
            return single(oltLocation(termination.getOltPort()));
        }
        return single(cableEndLocation(termination));
    }

    private CoordinateDto cableEndLocation(FiberTerminationEntity termination) {
        List<CableAttachmentEntity> elements =
            attachments.findAllByCable_IdOrderBySequenceNumberAsc(termination.getFiber().getCable().getId());
        if (elements.isEmpty()) {
            return null;
        }
        CableAttachmentEntity element = termination.getFiberEnd() == FiberEndEnum.A
            ? elements.get(0) : elements.get(elements.size() - 1);
        if (element.getBox() != null) {
            return boxLocation(element.getBox());
        }
        return element.getOlt() == null ? null : GeometryMapper.fromPoint(element.getOlt().getLocation());
    }

    // ---------- passos ----------

    TraceStep cableStep(FiberEntity fiber) {
        CableAssetEntity cable = fiber.getCable();
        return new TraceStep(TraceItemKindEnum.CABLE, cable.getId(), cable.getName(),
            new FiberInfo(fiber.getColor(), fiber.getAbsoluteNumber(), fiber.getTube().getColor()),
            null, GeometryMapper.fromLineString(cable.getLocation()));
    }

    TraceStep boxStep(OpticalConnectionEntity connection) {
        BoxAssetEntity box = connection.getBox();
        TracePosition position = splices.findByConnection_Id(connection.getId())
            .map(splice -> new TracePosition(splice.getTray().getTrayNumber(), splice.getPositionNumber()))
            .orElse(null);
        return new TraceStep(TraceItemKindEnum.BOX, box.getId(), box.getName(), null, position,
            single(boxLocation(box)));
    }

    TraceStep oltPortStep(OltPortEntity port) {
        String label = port.getOlt().getName() + " · placa " + port.getSlotNumber() + " · porta " + port.getPortNumber();
        return new TraceStep(TraceItemKindEnum.OLT_PORT, port.getId(), label, null, null,
            single(oltLocation(port)));
    }

    TraceStep splitterStep(SplitterEntity splitter) {
        return new TraceStep(TraceItemKindEnum.SPLITTER, splitter.getId(), splitterLabel(splitter), null, null,
            single(boxLocation(splitter.getBox())));
    }

    TraceStep servicePortStep(ServicePortEntity port) {
        return new TraceStep(TraceItemKindEnum.SERVICE_PORT, port.getId(), servicePortLabel(port), null, null,
            single(boxLocation(port.getBox())));
    }

    /** Porta de splitter como item de quebra (posição da caixa, pois a porta não tem posição própria). */
    TraceItem portItem(SplitterPortEntity port) {
        return new TraceItem(TraceItemKindEnum.SPLITTER_PORT, port.getId(), splitterPortLabel(port),
            single(boxLocation(port.getSplitter().getBox())));
    }

    TraceItem splitterItem(SplitterEntity splitter) {
        return new TraceItem(TraceItemKindEnum.SPLITTER, splitter.getId(), splitterLabel(splitter),
            single(boxLocation(splitter.getBox())));
    }

    /** Ponta de fibra como item de quebra. */
    TraceItem endItem(FiberTerminationEntity termination) {
        return new TraceItem(TraceItemKindEnum.FIBER_END, termination.getId(), fiberEndLabel(termination),
            positionOf(termination));
    }

    static TraceItem itemOf(TraceStep step) {
        return new TraceItem(step.kind(), step.id(), step.label(), step.coordinates());
    }

    private static CoordinateDto boxLocation(BoxAssetEntity box) {
        return GeometryMapper.fromPoint(box.getLocation());
    }

    private static CoordinateDto oltLocation(OltPortEntity port) {
        return GeometryMapper.fromPoint(port.getOlt().getLocation());
    }

    private static List<CoordinateDto> single(CoordinateDto coordinate) {
        return coordinate == null ? null : List.of(coordinate);
    }
}
