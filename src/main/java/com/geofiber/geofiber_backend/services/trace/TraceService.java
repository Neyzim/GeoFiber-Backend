package com.geofiber.geofiber_backend.services.trace;

import com.geofiber.geofiber_backend.dto.trace.TraceResponse;
import com.geofiber.geofiber_backend.dto.trace.TraceResponse.TraceItem;
import com.geofiber.geofiber_backend.entities.box.BoxAssetEntity;
import com.geofiber.geofiber_backend.entities.cable.FiberEntity;
import com.geofiber.geofiber_backend.entities.connection.OpticalConnectionEntity;
import com.geofiber.geofiber_backend.entities.splice.SpliceEntity;
import com.geofiber.geofiber_backend.entities.splitter.SplitterEntity;
import com.geofiber.geofiber_backend.entities.serviceport.ServicePortEntity;
import com.geofiber.geofiber_backend.entities.splitter.SplitterPortEntity;
import com.geofiber.geofiber_backend.entities.termination.FiberTerminationEntity;
import com.geofiber.geofiber_backend.enums.PortDirectionEnum;
import com.geofiber.geofiber_backend.enums.trace.TraceDirectionEnum;
import com.geofiber.geofiber_backend.enums.trace.TraceItemKindEnum;
import com.geofiber.geofiber_backend.enums.trace.TraceKindEnum;
import com.geofiber.geofiber_backend.enums.trace.TraceStatusEnum;
import com.geofiber.geofiber_backend.repositories.box.BoxRepository;
import com.geofiber.geofiber_backend.repositories.cable.CableAttachmentRepository;
import com.geofiber.geofiber_backend.repositories.cable.FiberRepository;
import com.geofiber.geofiber_backend.repositories.connection.OpticalConnectionRepository;
import com.geofiber.geofiber_backend.repositories.customer.CustomerRepository;
import com.geofiber.geofiber_backend.repositories.serviceport.ServicePortRepository;
import com.geofiber.geofiber_backend.repositories.splice.SpliceRepository;
import com.geofiber.geofiber_backend.repositories.splitter.SplitterPortRepository;
import com.geofiber.geofiber_backend.repositories.splitter.SplitterRepository;
import com.geofiber.geofiber_backend.repositories.termination.FiberTerminationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.UUID;

import static com.geofiber.geofiber_backend.services.common.ServiceErrors.notFound;

/**
 * Rastreio da fibra: a partir de um ponto, devolve o caminho do sinal até a OLT (UPSTREAM) ou até
 * os clientes (DOWNSTREAM). Só leitura.
 */
@Service
@Transactional(readOnly = true)
public class TraceService {
    private final BoxRepository boxes;
    private final TraceNetwork net;

    public TraceService(
        BoxRepository boxes,
        FiberRepository fibers,
        FiberTerminationRepository terminations,
        OpticalConnectionRepository connections,
        SpliceRepository splices,
        SplitterRepository splitters,
        SplitterPortRepository splitterPorts,
        ServicePortRepository servicePorts,
        CustomerRepository customers,
        CableAttachmentRepository attachments
    ) {
        this.boxes = boxes;
        this.net = new TraceNetwork(fibers, terminations, connections, splices, splitters, splitterPorts,
            servicePorts, customers, attachments);
    }

    public TraceResponse trace(TraceKindEnum kind, UUID id, TraceDirectionEnum direction) {
        boolean upstream = direction == TraceDirectionEnum.UPSTREAM;
        TraceWalk walk = new TraceWalk(net, new HashSet<>());
        TraceItem start = switch (kind) {
            case FIBER_END -> startFiberEnd(walk, id, upstream);
            case FIBER -> startFiber(walk, id, upstream);
            case BOX -> startBox(walk, id, upstream);
            case SPLITTER -> startSplitter(walk, id, upstream);
            case SPLICE -> startSplice(walk, id, upstream);
            case SPLITTER_PORT -> startSplitterPort(walk, id, upstream);
        };
        return new TraceResponse(start, direction, statusOf(walk),
            walk.steps(), walk.breaks(), walk.branches(), walk.customers());
    }

    /**
     * Caminho UPSTREAM de uma porta de atendimento, usado pelo cálculo de impacto. Mesma travessia
     * do rastreio: a porta vem de uma saída de splitter ou de uma fibra ligada diretamente.
     */
    public UpstreamPath upstreamOf(ServicePortEntity servicePort) {
        TraceWalk walk = new TraceWalk(net, new HashSet<>());
        FiberTerminationEntity fed = net.terminationOf(servicePort);
        if (fed != null) {
            walk.visit(fed);
            walk.fromFiberEnd(fed, true);
        } else if (servicePort.getSplitterPort() != null) {
            walk.upFromOutput(servicePort.getSplitterPort().getSplitter());
        }
        return walk.upstreamPath();
    }

    private TraceItem startFiberEnd(TraceWalk walk, UUID id, boolean upstream) {
        FiberTerminationEntity termination = net.termination(id);
        walk.visit(termination);
        walk.fromFiberEnd(termination, upstream);
        return start(TraceItemKindEnum.FIBER_END, termination.getId(), net.fiberEndLabel(termination));
    }

    private TraceItem startFiber(TraceWalk walk, UUID id, boolean upstream) {
        FiberEntity fiber = net.fiber(id);
        walk.fromFiber(fiber, upstream);
        return start(TraceItemKindEnum.FIBER, fiber.getId(), net.fiberLabel(fiber));
    }

    /**
     * Caixa: entra pela primeira entrada de splitter ligada a uma fibra. Sem splitter ligado, usa a
     * primeira conexão da caixa. Sem nenhuma, não há o que andar.
     */
    private TraceItem startBox(TraceWalk walk, UUID id, boolean upstream) {
        BoxAssetEntity box = boxes.findById(id).orElseThrow(notFound("Box"));
        for (SplitterEntity splitter : net.splittersOf(box)) {
            SplitterPortEntity input = net.inputOf(splitter);
            FiberTerminationEntity termination = input == null ? null : net.terminationOf(input);
            if (termination != null) {
                if (upstream) {
                    walk.visit(termination);
                    walk.cross(termination);
                } else {
                    walk.summarizeOutputs(splitter);
                }
                return start(TraceItemKindEnum.BOX, box.getId(), box.getName());
            }
        }
        OpticalConnectionEntity connection = net.firstConnectionOf(box);
        if (connection != null) {
            walk.fromConnection(connection, upstream);
        }
        return start(TraceItemKindEnum.BOX, box.getId(), box.getName());
    }

    private TraceItem startSplitter(TraceWalk walk, UUID id, boolean upstream) {
        SplitterEntity splitter = net.splitter(id);
        if (upstream) {
            SplitterPortEntity input = net.inputOf(splitter);
            FiberTerminationEntity termination = input == null ? null : net.terminationOf(input);
            if (termination != null) {
                walk.visit(termination);
                walk.cross(termination);
            }
        } else {
            if (!net.hasInputFiber(splitter)) {
                walk.noSignal(net.splitterItem(splitter));
            }
            walk.summarizeOutputs(splitter);
        }
        return start(TraceItemKindEnum.SPLITTER, splitter.getId(), net.splitterLabel(splitter));
    }

    private TraceItem startSplice(TraceWalk walk, UUID id, boolean upstream) {
        SpliceEntity splice = net.splice(id);
        walk.fromConnection(splice.getConnection(), upstream);
        return start(TraceItemKindEnum.SPLICE, splice.getId(), net.spliceLabel(splice));
    }

    private TraceItem startSplitterPort(TraceWalk walk, UUID id, boolean upstream) {
        SplitterPortEntity port = net.splitterPort(id);
        SplitterEntity splitter = port.getSplitter();
        if (port.getDirection() == PortDirectionEnum.INPUT) {
            if (upstream) {
                FiberTerminationEntity termination = net.terminationOf(port);
                if (termination != null) {
                    walk.visit(termination);
                    walk.cross(termination);
                }
            } else {
                if (!net.hasInputFiber(splitter)) {
                    walk.noSignal(net.portItem(port));
                }
                walk.summarizeOutputs(splitter);
            }
        } else if (upstream) {
            walk.upFromOutput(splitter);
        } else {
            walk.outputDown(port);
        }
        return start(TraceItemKindEnum.SPLITTER_PORT, port.getId(), net.splitterPortLabel(port));
    }

    /** Situação do caminho: chegou à OLT, chegou a clientes, parou numa quebra ou não tinha nada. */
    private static TraceStatusEnum statusOf(TraceWalk walk) {
        if (walk.reachedOlt()) {
            return TraceStatusEnum.REACHED_OLT;
        }
        if (walk.customerTotal() > 0) {
            return TraceStatusEnum.REACHED_CUSTOMERS;
        }
        if (walk.hasAnyBreak()) {
            return TraceStatusEnum.BROKEN;
        }
        return TraceStatusEnum.EMPTY;
    }

    private static TraceItem start(TraceItemKindEnum kind, UUID id, String label) {
        return new TraceItem(kind, id, label, null);
    }
}
