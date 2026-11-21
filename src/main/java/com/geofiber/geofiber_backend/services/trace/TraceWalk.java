package com.geofiber.geofiber_backend.services.trace;

import com.geofiber.geofiber_backend.dto.trace.TraceResponse.TraceBranch;
import com.geofiber.geofiber_backend.dto.trace.TraceResponse.TraceBreak;
import com.geofiber.geofiber_backend.dto.trace.TraceResponse.TraceCustomer;
import com.geofiber.geofiber_backend.dto.trace.TraceResponse.TraceItem;
import com.geofiber.geofiber_backend.dto.trace.TraceResponse.TraceStep;
import com.geofiber.geofiber_backend.entities.cable.FiberEntity;
import com.geofiber.geofiber_backend.entities.connection.OpticalConnectionEntity;
import com.geofiber.geofiber_backend.entities.customer.CustomerEntity;
import com.geofiber.geofiber_backend.entities.serviceport.ServicePortEntity;
import com.geofiber.geofiber_backend.entities.splitter.SplitterEntity;
import com.geofiber.geofiber_backend.entities.splitter.SplitterPortEntity;
import com.geofiber.geofiber_backend.entities.termination.FiberTerminationEntity;
import com.geofiber.geofiber_backend.enums.FiberEndEnum;
import com.geofiber.geofiber_backend.enums.PortDirectionEnum;
import com.geofiber.geofiber_backend.enums.trace.TraceBranchStatusEnum;
import com.geofiber.geofiber_backend.enums.trace.TraceBreakReasonEnum;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Percurso do sinal. A rede é uma cadeia: cada ponta tem a fibra e uma única ligação (porta,
 * conexão óptica ou nada). Por isso não há escolha ao andar, exceto na entrada de splitter, onde
 * a descida se abre em todas as saídas. Cada ponto é visitado uma vez; passar do limite para.
 *
 * <p>Cada método que anda termina a chamada com outro método de andar ou com uma quebra, então
 * uma quebra encerra o caminho sem estado extra.
 */
final class TraceWalk {
    static final int MAX_STEPS = 50;

    private final TraceNetwork net;
    private final Set<UUID> visited;
    private final List<TraceStep> steps = new ArrayList<>();
    private final List<TraceBreak> breaks = new ArrayList<>();
    private final List<TraceCustomer> customers = new ArrayList<>();
    private final List<TraceBranch> branches = new ArrayList<>();
    private final Set<UUID> cableIds = new HashSet<>();
    private final Set<UUID> boxIds = new HashSet<>();
    private final Set<UUID> oltIds = new HashSet<>();
    /** Clientes e problemas das saídas de splitters aninhados, que não aparecem na lista. */
    private int hiddenCustomers;
    private boolean hiddenBroken;
    private boolean reachedOlt;

    TraceWalk(TraceNetwork net, Set<UUID> visited) {
        this.net = net;
        this.visited = visited;
    }

    List<TraceStep> steps() {
        return steps;
    }

    List<TraceBreak> breaks() {
        return breaks;
    }

    List<TraceCustomer> customers() {
        return customers;
    }

    List<TraceBranch> branches() {
        return branches;
    }

    boolean reachedOlt() {
        return reachedOlt;
    }

    /** Cabos, caixas e OLTs atravessados pelos passos registrados até agora. */
    UpstreamPath upstreamPath() {
        return new UpstreamPath(cableIds, boxIds, oltIds);
    }

    /** Clientes alcançados, contando também os de saídas aninhadas. */
    int customerTotal() {
        return customers.size() + hiddenCustomers;
    }

    /** Há alguma quebra que não seja só "porta sem cliente"? Sinal de saída quebrada. */
    boolean hasProblem() {
        return hiddenBroken || breaks.stream().anyMatch(item -> item.reason() != TraceBreakReasonEnum.NO_CUSTOMER);
    }

    boolean hasAnyBreak() {
        return hiddenBroken || !breaks.isEmpty();
    }

    // ---------- pontos de partida ----------

    void visit(FiberTerminationEntity termination) {
        visited.add(termination.getId());
    }

    /** Partindo de uma ponta: se a direção pede a ligação dela, anda pela ligação; senão, pela fibra. */
    void fromFiberEnd(FiberTerminationEntity termination, boolean upstream) {
        FiberEndEnum wanted = upstream ? FiberEndEnum.A : FiberEndEnum.B;
        if (termination.getFiberEnd() == wanted) {
            attach(termination);
        } else {
            cross(termination);
        }
    }

    /** Partindo da fibra inteira: o primeiro passo é a própria fibra, depois a ligação do lado pedido. */
    void fromFiber(FiberEntity fiber, boolean upstream) {
        FiberTerminationEntity a = net.end(fiber, FiberEndEnum.A);
        FiberTerminationEntity b = net.end(fiber, FiberEndEnum.B);
        visited.add(a.getId());
        visited.add(b.getId());
        if (addStep(net.cableStep(fiber))) {
            attach(upstream ? a : b);
        }
    }

    /** Partindo de uma conexão (fusão ou conector): sai pelo lado pedido, sem voltar por ela. */
    void fromConnection(OpticalConnectionEntity connection, boolean upstream) {
        FiberEndEnum wanted = upstream ? FiberEndEnum.B : FiberEndEnum.A;
        FiberTerminationEntity a = connection.getTerminationA();
        FiberTerminationEntity b = connection.getTerminationB();
        visited.add(a.getId());
        visited.add(b.getId());
        FiberTerminationEntity side = a.getFiberEnd() == wanted ? a : b.getFiberEnd() == wanted ? b : a;
        cross(side);
    }

    /** Entrada de splitter sem sinal (usado quando o ponto de partida é o próprio splitter). */
    void noSignal(TraceItem at) {
        breaks.add(new TraceBreak(TraceBreakReasonEnum.NO_SIGNAL, at));
    }

    /** Descida pelo splitter: um resumo por saída, na numeração 1..N. */
    void summarizeOutputs(SplitterEntity splitter) {
        for (SplitterPortEntity output : net.outputsOf(splitter)) {
            TraceWalk branch = new TraceWalk(net, visited);
            branch.outputDown(output);
            int count = branch.customerTotal();
            TraceBranchStatusEnum status = count > 0 ? TraceBranchStatusEnum.CUSTOMERS
                : branch.hasProblem() ? TraceBranchStatusEnum.BROKEN : TraceBranchStatusEnum.FREE;
            branches.add(new TraceBranch(output.getPortNumber() - 1, status, count, output.getId()));
            hiddenCustomers += count;
            hiddenBroken |= status == TraceBranchStatusEnum.BROKEN;
        }
    }

    /** Subida que atravessa o splitter pela saída: passa pelo splitter e segue pela entrada. */
    void upFromOutput(SplitterEntity splitter) {
        if (addStep(net.splitterStep(splitter))) {
            boxIds.add(splitter.getBox().getId());
            upThroughInput(splitter);
        }
    }

    /** Saída de splitter descendo: porta de atendimento, ou fibra ligada à saída; livre não anda. */
    void outputDown(SplitterPortEntity output) {
        ServicePortEntity servicePort = net.servicePortOf(output);
        if (servicePort != null) {
            if (!net.hasInputFiber(output.getSplitter())) {
                noSignal(net.portItem(output));
            }
            serviceArrival(servicePort);
            return;
        }
        FiberTerminationEntity termination = net.terminationOf(output);
        if (termination == null) {
            return;
        }
        if (!net.hasInputFiber(output.getSplitter())) {
            noSignal(net.portItem(output));
        }
        if (!visited.add(termination.getId())) {
            breaks.add(loopAt(termination));
            return;
        }
        cross(termination);
    }

    // ---------- andar ----------

    /** Atravessa a fibra da ponta até a ponta irmã e segue por ela. */
    void cross(FiberTerminationEntity termination) {
        FiberTerminationEntity other = net.otherEnd(termination);
        if (!addStep(net.cableStep(termination.getFiber()))) {
            return;
        }
        cableIds.add(termination.getFiber().getCable().getId());
        if (!visited.add(other.getId())) {
            breaks.add(loopAt(other));
            return;
        }
        attach(other);
    }

    /** Chegou à ponta vinda da fibra: segue pela ligação dela. */
    private void attach(FiberTerminationEntity termination) {
        if (termination.getOltPort() != null) {
            if (addStep(net.oltPortStep(termination.getOltPort()))) {
                reachedOlt = true;
                oltIds.add(termination.getOltPort().getOlt().getId());
            }
            return;
        }
        if (termination.getServicePort() != null) {
            serviceArrival(termination.getServicePort());
            return;
        }
        if (termination.getSplitterPort() != null) {
            splitterArrival(termination.getSplitterPort());
            return;
        }
        OpticalConnectionEntity connection = net.connectionOf(termination);
        if (connection == null) {
            breaks.add(new TraceBreak(TraceBreakReasonEnum.NO_CONNECTION, net.endItem(termination)));
            return;
        }
        FiberTerminationEntity partner = connection.getTerminationA().getId().equals(termination.getId())
            ? connection.getTerminationB() : connection.getTerminationA();
        if (!addStep(net.boxStep(connection))) {
            return;
        }
        boxIds.add(connection.getBox().getId());
        if (!visited.add(partner.getId())) {
            breaks.add(loopAt(partner));
            return;
        }
        cross(partner);
    }

    private void splitterArrival(SplitterPortEntity port) {
        if (port.getDirection() == PortDirectionEnum.INPUT) {
            if (addStep(net.splitterStep(port.getSplitter()))) {
                summarizeOutputs(port.getSplitter());
            }
        } else {
            upFromOutput(port.getSplitter());
        }
    }

    /** Subindo pela entrada do splitter: segue pela fibra ligada a ela. */
    void upThroughInput(SplitterEntity splitter) {
        SplitterPortEntity input = net.inputOf(splitter);
        FiberTerminationEntity termination = input == null ? null : net.terminationOf(input);
        if (termination == null) {
            noSignal(net.splitterItem(splitter));
            return;
        }
        if (!visited.add(termination.getId())) {
            breaks.add(loopAt(termination));
            return;
        }
        cross(termination);
    }

    private void serviceArrival(ServicePortEntity servicePort) {
        TraceStep step = net.servicePortStep(servicePort);
        if (!addStep(step)) {
            return;
        }
        CustomerEntity customer = net.customerOf(servicePort);
        if (customer == null) {
            breaks.add(new TraceBreak(TraceBreakReasonEnum.NO_CUSTOMER, TraceNetwork.itemOf(step)));
        } else {
            customers.add(new TraceCustomer(customer.getId(), customer.getName()));
        }
    }

    // ---------- registro ----------

    /** Acrescenta um passo; no limite, registra a quebra e devolve falso. */
    private boolean addStep(TraceStep step) {
        if (steps.size() >= MAX_STEPS) {
            breaks.add(new TraceBreak(TraceBreakReasonEnum.DEPTH_LIMIT, TraceNetwork.itemOf(step)));
            return false;
        }
        steps.add(step);
        return true;
    }

    private TraceBreak loopAt(FiberTerminationEntity termination) {
        return new TraceBreak(TraceBreakReasonEnum.LOOP, net.endItem(termination));
    }
}
