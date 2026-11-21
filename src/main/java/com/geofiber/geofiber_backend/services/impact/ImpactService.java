package com.geofiber.geofiber_backend.services.impact;

import com.geofiber.geofiber_backend.dto.geo.CoordinateDto;
import com.geofiber.geofiber_backend.dto.impact.ImpactResponse;
import com.geofiber.geofiber_backend.dto.impact.ImpactResponse.ImpactBox;
import com.geofiber.geofiber_backend.dto.impact.ImpactResponse.ImpactCustomer;
import com.geofiber.geofiber_backend.dto.impact.ImpactResponse.ImpactOlt;
import com.geofiber.geofiber_backend.dto.impact.ImpactResponse.ImpactTarget;
import com.geofiber.geofiber_backend.dto.impact.ImpactResponse.ImpactTotals;
import com.geofiber.geofiber_backend.entities.box.BoxAssetEntity;
import com.geofiber.geofiber_backend.entities.cable.CableAssetEntity;
import com.geofiber.geofiber_backend.entities.customer.CustomerEntity;
import com.geofiber.geofiber_backend.entities.olt.OltAssetEntity;
import com.geofiber.geofiber_backend.enums.customer.CustomerStatusEnum;
import com.geofiber.geofiber_backend.enums.impact.ImpactTargetKindEnum;
import com.geofiber.geofiber_backend.repositories.box.BoxRepository;
import com.geofiber.geofiber_backend.repositories.cable.CableRepository;
import com.geofiber.geofiber_backend.repositories.customer.CustomerRepository;
import com.geofiber.geofiber_backend.repositories.olt.OltRepository;
import com.geofiber.geofiber_backend.services.geo.GeometryMapper;
import com.geofiber.geofiber_backend.services.trace.TraceService;
import com.geofiber.geofiber_backend.services.trace.UpstreamPath;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.geofiber.geofiber_backend.services.common.ServiceErrors.notFound;

/**
 * Impacto de manutenção: quais clientes dependem de um cabo, caixa ou OLT, pelo caminho UPSTREAM
 * que o sinal faz até a OLT. Só leitura, sem cache.
 */
@Service
@Transactional(readOnly = true)
public class ImpactService {
    static final int MAX_LISTED_CUSTOMERS = 2000;

    private static final Comparator<CustomerPath> BY_CUSTOMER =
        Comparator.comparing((CustomerPath path) -> path.customer().getName())
            .thenComparing(path -> path.customer().getId());
    private static final Comparator<BoxAssetEntity> BY_BOX =
        Comparator.comparing(BoxAssetEntity::getName).thenComparing(BoxAssetEntity::getId);
    private static final Comparator<OltAssetEntity> BY_OLT =
        Comparator.comparing(OltAssetEntity::getName).thenComparing(OltAssetEntity::getId);

    private final CableRepository cables;
    private final BoxRepository boxes;
    private final OltRepository olts;
    private final CustomerRepository customers;
    private final TraceService trace;

    public ImpactService(CableRepository cables, BoxRepository boxes, OltRepository olts,
                         CustomerRepository customers, TraceService trace) {
        this.cables = cables;
        this.boxes = boxes;
        this.olts = olts;
        this.customers = customers;
        this.trace = trace;
    }

    public ImpactResponse cable(UUID id) {
        CableAssetEntity cable = cables.findById(id).orElseThrow(notFound("Cable"));
        ImpactTarget target = new ImpactTarget(ImpactTargetKindEnum.CABLE, cable.getId(), cable.getName(),
            GeometryMapper.fromLineString(cable.getLocation()));
        List<CustomerPath> affected = loadPaths().stream()
            .filter(path -> path.upstream().cableIds().contains(id))
            .toList();
        return assemble(target, affected, null, null);
    }

    public ImpactResponse box(UUID id) {
        BoxAssetEntity box = boxes.findById(id).orElseThrow(notFound("Box"));
        ImpactTarget target = new ImpactTarget(ImpactTargetKindEnum.BOX, box.getId(), box.getName(),
            point(box.getLocation()));
        List<CustomerPath> affected = loadPaths().stream()
            .filter(path -> boxesOf(path).contains(id))
            .toList();
        return assemble(target, affected, id, null);
    }

    public ImpactResponse olt(UUID id) {
        OltAssetEntity olt = olts.findById(id).orElseThrow(notFound("OLT"));
        ImpactTarget target = new ImpactTarget(ImpactTargetKindEnum.OLT, olt.getId(), olt.getName(),
            point(olt.getLocation()));
        List<CustomerPath> affected = loadPaths().stream()
            .filter(path -> path.upstream().oltIds().contains(id))
            .toList();
        return assemble(target, affected, null, id);
    }

    /** Caminho UPSTREAM de cada cliente com sinal. Um cliente aparece uma vez, mesmo com vários caminhos. */
    private List<CustomerPath> loadPaths() {
        return customers.findAllByStatusNotAndServicePortIsNotNull(CustomerStatusEnum.CANCELLED).stream()
            .map(customer -> new CustomerPath(customer,
                trace.upstreamOf(customer.getServicePort()),
                customer.getServicePort().getBox().getId()))
            .sorted(BY_CUSTOMER)
            .toList();
    }

    /**
     * Monta a resposta. Totais usam todos os afetados; as listas usam só os primeiros
     * {@link #MAX_LISTED_CUSTOMERS} clientes, em ordem de nome.
     */
    private ImpactResponse assemble(ImpactTarget target, List<CustomerPath> affected,
                                    UUID excludedBoxId, UUID excludedOltId) {
        Set<UUID> boxIds = new HashSet<>();
        Set<UUID> oltIds = new HashSet<>();
        for (CustomerPath path : affected) {
            boxIds.addAll(boxesOf(path));
            oltIds.addAll(path.upstream().oltIds());
        }
        boxIds.remove(excludedBoxId);
        oltIds.remove(excludedOltId);

        List<CustomerPath> listed = affected.subList(0, Math.min(affected.size(), MAX_LISTED_CUSTOMERS));

        Map<UUID, BoxAssetEntity> boxById = new HashMap<>();
        boxes.findAllById(boxIds).forEach(box -> boxById.put(box.getId(), box));
        List<ImpactBox> boxList = boxIds.stream()
            .map(boxById::get)
            .sorted(BY_BOX)
            .map(box -> boxEntry(box, listed))
            .filter(entry -> !entry.customers().isEmpty())
            .toList();

        List<ImpactOlt> oltList = olts.findAllById(oltIds).stream()
            .sorted(BY_OLT)
            .map(olt -> new ImpactOlt(olt.getId(), olt.getName(), point(olt.getLocation())))
            .toList();

        return new ImpactResponse(
            target,
            new ImpactTotals(affected.size(), boxIds.size(), oltIds.size()),
            boxList,
            oltList,
            affected.size() > MAX_LISTED_CUSTOMERS
        );
    }

    private static ImpactBox boxEntry(BoxAssetEntity box, List<CustomerPath> listed) {
        List<ImpactCustomer> inBox = listed.stream()
            .filter(path -> boxesOf(path).contains(box.getId()))
            .map(path -> customerDto(path.customer()))
            .toList();
        return new ImpactBox(box.getId(), box.getName(), point(box.getLocation()), inBox);
    }

    /** Caixas por onde o sinal do cliente passa: as do caminho e a da própria porta de atendimento. */
    private static Set<UUID> boxesOf(CustomerPath path) {
        Set<UUID> ids = new HashSet<>(path.upstream().boxIds());
        ids.add(path.ownBoxId());
        return ids;
    }

    private static ImpactCustomer customerDto(CustomerEntity customer) {
        return new ImpactCustomer(customer.getId(), customer.getName(), point(customer.getLocation()));
    }

    private static List<CoordinateDto> point(Point point) {
        return List.of(GeometryMapper.fromPoint(point));
    }

    private record CustomerPath(CustomerEntity customer, UpstreamPath upstream, UUID ownBoxId) {
    }
}
