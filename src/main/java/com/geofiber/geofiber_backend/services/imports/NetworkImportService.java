package com.geofiber.geofiber_backend.services.imports;

import com.geofiber.geofiber_backend.services.common.OwnershipGuard;

import com.geofiber.geofiber_backend.dto.imports.NetworkImportRequest;
import com.geofiber.geofiber_backend.dto.imports.NetworkImportRequest.CableItem;
import com.geofiber.geofiber_backend.dto.imports.NetworkImportRequest.ElementItem;
import com.geofiber.geofiber_backend.dto.imports.NetworkImportRequest.EndpointRef;
import com.geofiber.geofiber_backend.dto.imports.NetworkImportResponse;
import com.geofiber.geofiber_backend.entities.box.BoxAssetEntity;
import com.geofiber.geofiber_backend.entities.cable.CableAssetEntity;
import com.geofiber.geofiber_backend.entities.cable.CableAttachmentEntity;
import com.geofiber.geofiber_backend.entities.olt.OltAssetEntity;
import com.geofiber.geofiber_backend.enums.InfrastructureStatusEnum;
import com.geofiber.geofiber_backend.enums.box.BoxTypeEnum;
import com.geofiber.geofiber_backend.enums.cable.CableAttachmentRoleEnum;
import com.geofiber.geofiber_backend.enums.imports.ImportActionEnum;
import com.geofiber.geofiber_backend.enums.imports.ImportKindEnum;
import com.geofiber.geofiber_backend.repositories.box.BoxRepository;
import com.geofiber.geofiber_backend.repositories.cable.CableAttachmentRepository;
import com.geofiber.geofiber_backend.repositories.cable.CableRepository;
import com.geofiber.geofiber_backend.repositories.olt.OltRepository;
import com.geofiber.geofiber_backend.repositories.termination.FiberTerminationRepository;
import com.geofiber.geofiber_backend.services.common.NameNormalizer;
import com.geofiber.geofiber_backend.services.geo.GeometryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.geofiber.geofiber_backend.services.common.ServiceErrors.badRequest;
import static com.geofiber.geofiber_backend.services.common.ServiceErrors.conflict;
import static com.geofiber.geofiber_backend.services.common.ServiceErrors.notFound;

/**
 * Grava uma lista pronta de elementos, cabos e pontas numa única transação. Primeiro confere tudo
 * (400, 404, 409) sem gravar nada; depois grava. Não apaga nada e não cria fibra, tubo, fusão, splitter,
 * porta nem cliente.
 */
@Service
@Transactional
public class NetworkImportService {
    static final int MAX_ITEMS = 10_000;
    private static final String DUPLICATE_MESSAGE = "Import has elements that already exist";
    private static final String CONNECTED_CABLE_MESSAGE = "Cable has connected fibers and cannot be reshaped";

    private final BoxRepository boxRepository;
    private final OltRepository oltRepository;
    private final CableRepository cableRepository;
    private final CableAttachmentRepository attachmentRepository;
    private final FiberTerminationRepository terminationRepository;

    private final OwnershipGuard ownership;

    public NetworkImportService(
        BoxRepository boxRepository,
        OltRepository oltRepository,
        CableRepository cableRepository,
        CableAttachmentRepository attachmentRepository,
        FiberTerminationRepository terminationRepository,
        OwnershipGuard ownership
    ) {
        this.ownership = ownership;
        this.boxRepository = boxRepository;
        this.oltRepository = oltRepository;
        this.cableRepository = cableRepository;
        this.attachmentRepository = attachmentRepository;
        this.terminationRepository = terminationRepository;
    }

    public NetworkImportResponse importNetwork(NetworkImportRequest request) {
        List<ElementItem> elements = orEmpty(request.elements());
        List<CableItem> cables = orEmpty(request.cables());
        if (elements.size() + cables.size() > MAX_ITEMS) {
            throw badRequest("Import exceeds " + MAX_ITEMS + " items");
        }
        NetworkImportValidator.validate(request.usageStatus(), elements, cables);

        // Fase 1: conferências. Nada é gravado aqui.
        Map<String, BoxAssetEntity> movedBoxes = loadMovedBoxes(elements);
        Map<String, OltAssetEntity> movedOlts = loadMovedOlts(elements);
        Map<String, CableAssetEntity> reshapedCables = loadReshapedCables(cables);
        checkEndpointsExist(cables);
        checkNoDuplicateElements(elements);

        // Fase 2: gravação.
        Map<String, UUID> ids = new LinkedHashMap<>();
        Map<String, BoxAssetEntity> createdBoxes = new HashMap<>();
        Map<String, OltAssetEntity> createdOlts = new HashMap<>();
        InfrastructureStatusEnum usageStatus = request.usageStatus();

        for (ElementItem item : elements) {
            if (item.action() != ImportActionEnum.CREATE) {
                continue;
            }
            if (item.kind() == ImportKindEnum.BOX) {
                BoxAssetEntity box = boxRepository.saveAndFlush(newBox(item, usageStatus));
                createdBoxes.put(item.ref(), box);
                ids.put(item.ref(), box.getId());
            } else {
                OltAssetEntity olt = oltRepository.saveAndFlush(newOlt(item, usageStatus));
                createdOlts.put(item.ref(), olt);
                ids.put(item.ref(), olt.getId());
            }
        }

        int moved = 0;
        for (ElementItem item : elements) {
            if (item.action() != ImportActionEnum.MOVE) {
                continue;
            }
            if (item.kind() == ImportKindEnum.BOX) {
                BoxAssetEntity box = movedBoxes.get(item.ref());
                box.setLocation(GeometryMapper.toPoint(item.location()));
                ids.put(item.ref(), box.getId());
            } else {
                OltAssetEntity olt = movedOlts.get(item.ref());
                olt.setLocation(GeometryMapper.toPoint(item.location()));
                ids.put(item.ref(), olt.getId());
            }
            moved++;
        }

        int attachments = 0;
        for (CableItem item : cables) {
            if (item.action() != ImportActionEnum.CREATE) {
                continue;
            }
            CableAssetEntity cable = cableRepository.saveAndFlush(newCable(item, usageStatus));
            ids.put(item.ref(), cable.getId());
            List<CableAttachmentEntity> created = new ArrayList<>();
            int sequence = 1;
            if (item.start() != null) {
                created.add(newAttachment(cable, sequence++, CableAttachmentRoleEnum.START, item.start(),
                    createdBoxes, createdOlts));
            }
            if (item.end() != null) {
                created.add(newAttachment(cable, sequence, CableAttachmentRoleEnum.END, item.end(),
                    createdBoxes, createdOlts));
            }
            attachments += attachmentRepository.saveAllAndFlush(created).size();
        }

        int reshaped = 0;
        for (CableItem item : cables) {
            if (item.action() != ImportActionEnum.RESHAPE) {
                continue;
            }
            CableAssetEntity cable = reshapedCables.get(item.ref());
            cable.setLocation(GeometryMapper.toLineString(item.coordinates()));
            cableRepository.saveAndFlush(cable);
            ids.put(item.ref(), cable.getId());
            reshaped++;
        }

        NetworkImportResponse.Created created = new NetworkImportResponse.Created(
            createdBoxes.size(), createdOlts.size(),
            (int) cables.stream().filter(item -> item.action() == ImportActionEnum.CREATE).count(),
            attachments);
        return new NetworkImportResponse(created, moved, reshaped, ids);
    }

    private Map<String, BoxAssetEntity> loadMovedBoxes(List<ElementItem> elements) {
        Map<String, BoxAssetEntity> boxes = new HashMap<>();
        for (ElementItem item : elements) {
            if (item.action() == ImportActionEnum.MOVE && item.kind() == ImportKindEnum.BOX) {
                BoxAssetEntity box = boxRepository.findById(item.id()).orElseThrow(notFound("Box"));
                ownership.requireOwner(box.getCreatedBy());
                boxes.put(item.ref(), box);
            }
        }
        return boxes;
    }

    private Map<String, OltAssetEntity> loadMovedOlts(List<ElementItem> elements) {
        Map<String, OltAssetEntity> olts = new HashMap<>();
        for (ElementItem item : elements) {
            if (item.action() == ImportActionEnum.MOVE && item.kind() == ImportKindEnum.OLT) {
                OltAssetEntity olt = oltRepository.findById(item.id()).orElseThrow(notFound("OLT"));
                ownership.requireOwner(olt.getCreatedBy());
                olts.put(item.ref(), olt);
            }
        }
        return olts;
    }

    private Map<String, CableAssetEntity> loadReshapedCables(List<CableItem> cables) {
        Map<String, CableAssetEntity> reshaped = new HashMap<>();
        for (CableItem item : cables) {
            if (item.action() != ImportActionEnum.RESHAPE) {
                continue;
            }
            CableAssetEntity cable = cableRepository.findById(item.id()).orElseThrow(notFound("Cable"));
            ownership.requireOwner(cable.getCreatedBy());
            if (terminationRepository.existsConnectedFiberInCable(cable.getId())) {
                throw conflict(CONNECTED_CABLE_MESSAGE);
            }
            reshaped.put(item.ref(), cable);
        }
        return reshaped;
    }

    /** Pontas com boxId/oltId precisam apontar para elementos que já existem na rede. */
    private void checkEndpointsExist(List<CableItem> cables) {
        List<ImportItemError> errors = new ArrayList<>();
        for (CableItem cable : cables) {
            if (cable.action() != ImportActionEnum.CREATE) {
                continue;
            }
            checkEndpointExists(cable.ref(), "start", cable.start(), errors);
            checkEndpointExists(cable.ref(), "end", cable.end(), errors);
        }
        if (!errors.isEmpty()) {
            throw new ImportValidationException(errors);
        }
    }

    private void checkEndpointExists(String cableRef, String field, EndpointRef endpoint,
                                     List<ImportItemError> errors) {
        if (endpoint == null) {
            return;
        }
        if (endpoint.boxId() != null && !boxRepository.existsById(endpoint.boxId())) {
            errors.add(new ImportItemError(cableRef, field, "box not found"));
        }
        if (endpoint.oltId() != null && !oltRepository.existsById(endpoint.oltId())) {
            errors.add(new ImportItemError(cableRef, field, "OLT not found"));
        }
    }

    /**
     * Mesmo tipo e mesmo nome normalizado já na rede, ou repetidos na própria importação, é duplicado.
     * Cabos não entram nesta regra.
     */
    private void checkNoDuplicateElements(List<ElementItem> elements) {
        Set<String> keys = new HashSet<>();
        boxRepository.findAll().forEach(box -> keys.add(boxKey(box.getType(), box.getName())));
        oltRepository.findAll().forEach(olt -> keys.add(oltKey(olt.getName())));
        for (ElementItem item : elements) {
            if (item.action() != ImportActionEnum.CREATE) {
                continue;
            }
            String key = item.kind() == ImportKindEnum.BOX ? boxKey(item.type(), item.name()) : oltKey(item.name());
            if (!keys.add(key)) {
                throw conflict(DUPLICATE_MESSAGE);
            }
        }
    }

    private static String boxKey(BoxTypeEnum type, String name) {
        return "BOX:" + type.name() + ":" + NameNormalizer.normalize(name);
    }

    private static String oltKey(String name) {
        return "OLT:" + NameNormalizer.normalize(name);
    }

    private BoxAssetEntity newBox(ElementItem item, InfrastructureStatusEnum usageStatus) {
        BoxAssetEntity box = new BoxAssetEntity();
        box.setCreatedBy(ownership.currentUserId());
        box.setName(item.name().strip());
        box.setDescription(blankToNull(item.description()));
        box.setType(item.type());
        box.setUsageStatus(usageStatus);
        box.setLocation(GeometryMapper.toPoint(item.location()));
        return box;
    }

    private OltAssetEntity newOlt(ElementItem item, InfrastructureStatusEnum usageStatus) {
        OltAssetEntity olt = new OltAssetEntity();
        olt.setCreatedBy(ownership.currentUserId());
        olt.setName(item.name().strip());
        olt.setDescription(blankToNull(item.description()));
        olt.setUsageStatus(usageStatus);
        olt.setLocation(GeometryMapper.toPoint(item.location()));
        return olt;
    }

    private CableAssetEntity newCable(CableItem item, InfrastructureStatusEnum usageStatus) {
        CableAssetEntity cable = new CableAssetEntity();
        cable.setCreatedBy(ownership.currentUserId());
        cable.setName(item.name().strip());
        cable.setDescription(blankToNull(item.description()));
        cable.setRole(item.role());
        cable.setUsageStatus(usageStatus);
        cable.setFiberCapacity(item.fiberCapacity());
        cable.setLocation(GeometryMapper.toLineString(item.coordinates()));
        return cable;
    }

    private CableAttachmentEntity newAttachment(
        CableAssetEntity cable, int sequence, CableAttachmentRoleEnum role, EndpointRef endpoint,
        Map<String, BoxAssetEntity> createdBoxes, Map<String, OltAssetEntity> createdOlts
    ) {
        CableAttachmentEntity attachment = new CableAttachmentEntity();
        attachment.setCable(cable);
        attachment.setSequenceNumber(sequence);
        attachment.setRole(role);
        if (endpoint.ref() != null) {
            BoxAssetEntity box = createdBoxes.get(endpoint.ref());
            if (box != null) {
                attachment.setBox(box);
            } else {
                attachment.setOlt(createdOlts.get(endpoint.ref()));
            }
        } else if (endpoint.boxId() != null) {
            attachment.setBox(boxRepository.findById(endpoint.boxId()).orElseThrow(notFound("Box")));
        } else {
            attachment.setOlt(oltRepository.findById(endpoint.oltId()).orElseThrow(notFound("OLT")));
        }
        return attachment;
    }

    private static <T> List<T> orEmpty(List<T> items) {
        return items == null ? List.of() : items;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
