package com.geofiber.geofiber_backend.services.imports;

import com.geofiber.geofiber_backend.dto.geo.CoordinateDto;
import com.geofiber.geofiber_backend.dto.imports.NetworkImportRequest.CableItem;
import com.geofiber.geofiber_backend.dto.imports.NetworkImportRequest.ElementItem;
import com.geofiber.geofiber_backend.dto.imports.NetworkImportRequest.EndpointRef;
import com.geofiber.geofiber_backend.enums.InfrastructureStatusEnum;
import com.geofiber.geofiber_backend.enums.imports.ImportActionEnum;
import com.geofiber.geofiber_backend.enums.imports.ImportKindEnum;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Confere a forma de cada item da importação (campos, faixas, referências internas) antes de qualquer
 * consulta ou gravação. Coleta todos os erros e lança uma única {@link ImportValidationException}.
 */
final class NetworkImportValidator {
    static final int MAX_NAME_LENGTH = 160;
    static final int MAX_DESCRIPTION_LENGTH = 1000;
    private static final int MIN_CABLE_POINTS = 2;

    private final List<ImportItemError> errors = new ArrayList<>();

    private NetworkImportValidator() {
    }

    static void validate(InfrastructureStatusEnum usageStatus, List<ElementItem> elements, List<CableItem> cables) {
        NetworkImportValidator validator = new NetworkImportValidator();
        validator.checkUsageStatus(usageStatus);
        validator.checkItemsPresent(elements, cables);
        validator.checkRefsUnique(elements, cables);

        Set<String> createdElementRefs = new HashSet<>();
        for (ElementItem element : elements) {
            if (element != null) {
                validator.checkElement(element);
                if (element.action() == ImportActionEnum.CREATE && element.ref() != null) {
                    createdElementRefs.add(element.ref());
                }
            }
        }
        for (CableItem cable : cables) {
            if (cable != null) {
                validator.checkCable(cable, createdElementRefs);
            }
        }
        if (!validator.errors.isEmpty()) {
            throw new ImportValidationException(validator.errors);
        }
    }

    private void checkUsageStatus(InfrastructureStatusEnum usageStatus) {
        if (usageStatus == null) {
            error(null, "usageStatus", "is required");
        }
    }

    private void checkItemsPresent(List<ElementItem> elements, List<CableItem> cables) {
        if (elements.stream().anyMatch(Objects::isNull)) {
            error(null, "elements", "must not contain null items");
        }
        if (cables.stream().anyMatch(Objects::isNull)) {
            error(null, "cables", "must not contain null items");
        }
    }

    private void checkRefsUnique(List<ElementItem> elements, List<CableItem> cables) {
        Set<String> seen = new HashSet<>();
        List<String> refs = new ArrayList<>();
        elements.stream().filter(Objects::nonNull).forEach(item -> refs.add(item.ref()));
        cables.stream().filter(Objects::nonNull).forEach(item -> refs.add(item.ref()));
        for (String ref : refs) {
            if (ref == null || ref.isBlank()) {
                error(null, "ref", "is required");
            } else if (!seen.add(ref)) {
                error(ref, "ref", "is repeated in the import");
            }
        }
    }

    private void checkElement(ElementItem element) {
        String ref = element.ref();
        if (element.action() == null) {
            error(ref, "action", "is required");
            return;
        }
        if (element.action() != ImportActionEnum.CREATE && element.action() != ImportActionEnum.MOVE) {
            error(ref, "action", "must be CREATE or MOVE for elements");
            return;
        }
        if (element.kind() == null) {
            error(ref, "kind", "is required");
        }
        checkCoordinate(ref, "location", element.location());
        if (element.action() == ImportActionEnum.CREATE) {
            checkType(element);
            checkName(ref, element.name());
            checkDescription(ref, element.description());
        } else if (element.id() == null) {
            error(ref, "id", "is required for MOVE");
        }
    }

    /** Tipo só é exigido em CREATE: o MOVE identifica o elemento pelo id e não traz tipo. */
    private void checkType(ElementItem element) {
        String ref = element.ref();
        if (element.kind() == ImportKindEnum.BOX && element.type() == null) {
            error(ref, "type", "is required for BOX and must be CTO, CEO or DIO");
        } else if (element.kind() == ImportKindEnum.OLT && element.type() != null) {
            error(ref, "type", "must be omitted for OLT");
        }
    }

    private void checkCable(CableItem cable, Set<String> createdElementRefs) {
        String ref = cable.ref();
        if (cable.action() == null) {
            error(ref, "action", "is required");
            return;
        }
        switch (cable.action()) {
            case CREATE -> {
                checkName(ref, cable.name());
                checkDescription(ref, cable.description());
                if (cable.role() == null) {
                    error(ref, "role", "is required");
                }
                if (cable.fiberCapacity() == null || cable.fiberCapacity() <= 0) {
                    error(ref, "fiberCapacity", "must be an integer greater than zero");
                }
                checkCoordinates(ref, cable.coordinates());
                checkEndpoints(cable, createdElementRefs);
            }
            case RESHAPE -> {
                if (cable.id() == null) {
                    error(ref, "id", "is required for RESHAPE");
                }
                checkCoordinates(ref, cable.coordinates());
                if (cable.start() != null || cable.end() != null) {
                    error(ref, "start", "only applies to CREATE");
                }
            }
            default -> error(ref, "action", "must be CREATE or RESHAPE for cables");
        }
    }

    private void checkEndpoints(CableItem cable, Set<String> createdElementRefs) {
        checkEndpoint(cable.ref(), "start", cable.start(), createdElementRefs);
        checkEndpoint(cable.ref(), "end", cable.end(), createdElementRefs);
        if (cable.start() != null && cable.end() != null
            && endpointKey(cable.start()) != null && endpointKey(cable.start()).equals(endpointKey(cable.end()))) {
            error(cable.ref(), "end", "An element can appear only once in the cable");
        }
    }

    private void checkEndpoint(String cableRef, String field, EndpointRef endpoint, Set<String> createdElementRefs) {
        if (endpoint == null) {
            return;
        }
        int keys = 0;
        keys += endpoint.ref() != null ? 1 : 0;
        keys += endpoint.boxId() != null ? 1 : 0;
        keys += endpoint.oltId() != null ? 1 : 0;
        if (keys != 1) {
            error(cableRef, field, "must reference exactly one of ref, boxId or oltId");
        } else if (endpoint.ref() != null && !createdElementRefs.contains(endpoint.ref())) {
            error(cableRef, field, "ref must point to a CREATE element of this import");
        }
    }

    private static String endpointKey(EndpointRef endpoint) {
        if (endpoint.ref() != null) {
            return "ref:" + endpoint.ref();
        }
        if (endpoint.boxId() != null) {
            return "box:" + endpoint.boxId();
        }
        return endpoint.oltId() != null ? "olt:" + endpoint.oltId() : null;
    }

    private void checkCoordinates(String ref, List<CoordinateDto> coordinates) {
        if (coordinates == null || coordinates.size() < MIN_CABLE_POINTS) {
            error(ref, "coordinates", "must have at least 2 points");
            return;
        }
        for (CoordinateDto coordinate : coordinates) {
            checkCoordinate(ref, "coordinates", coordinate);
        }
    }

    private void checkCoordinate(String ref, String field, CoordinateDto coordinate) {
        if (coordinate == null) {
            error(ref, field, "is required");
            return;
        }
        if (coordinate.longitude() == null || Math.abs(coordinate.longitude()) > 180) {
            error(ref, field, "longitude must be between -180 and 180");
        }
        if (coordinate.latitude() == null || Math.abs(coordinate.latitude()) > 90) {
            error(ref, field, "latitude must be between -90 and 90");
        }
    }

    private void checkName(String ref, String name) {
        if (name == null || name.isBlank()) {
            error(ref, "name", "must not be blank");
        } else if (name.strip().length() > MAX_NAME_LENGTH) {
            error(ref, "name", "must have at most " + MAX_NAME_LENGTH + " characters");
        }
    }

    private void checkDescription(String ref, String description) {
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            error(ref, "description", "must have at most " + MAX_DESCRIPTION_LENGTH + " characters");
        }
    }

    private void error(String ref, String field, String message) {
        errors.add(new ImportItemError(ref, field, message));
    }
}
