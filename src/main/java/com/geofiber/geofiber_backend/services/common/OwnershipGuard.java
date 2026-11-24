package com.geofiber.geofiber_backend.services.common;

import com.geofiber.geofiber_backend.security.CurrentUserProvider;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.geofiber.geofiber_backend.services.common.ServiceErrors.forbidden;

/**
 * Regra de dono: só o criador de um elemento o altera ou apaga. Administrador não ganha
 * permissão extra sobre elementos de outras pessoas.
 */
@Component
public class OwnershipGuard {
    private final CurrentUserProvider currentUser;

    public OwnershipGuard(CurrentUserProvider currentUser) {
        this.currentUser = currentUser;
    }

    public UUID currentUserId() {
        return currentUser.current().id();
    }

    public boolean isOwner(UUID createdBy) {
        return createdBy != null && createdBy.equals(currentUserId());
    }

    public void requireOwner(UUID createdBy) {
        if (!isOwner(createdBy)) {
            throw forbidden("Só o criador pode alterar este elemento.");
        }
    }

    /** Ligações entre dois elementos: basta ser dono de um dos dois. */
    public void requireOwnerOfAny(UUID first, UUID second) {
        if (!isOwner(first) && !isOwner(second)) {
            throw forbidden("Só o dono de um dos elementos pode alterar esta ligação.");
        }
    }
}
