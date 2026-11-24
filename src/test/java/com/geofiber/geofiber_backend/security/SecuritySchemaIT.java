package com.geofiber.geofiber_backend.security;

import com.geofiber.geofiber_backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SecuritySchemaIT extends IntegrationTest {

    @Test
    void appUserEmailIsUnique() {
        insertUser("a@x.com");

        assertThrows(DataIntegrityViolationException.class, () -> insertUser("a@x.com"));
    }

    private void insertUser(String email) {
        jdbc.update("INSERT INTO app_user (id, name, email, password_hash, role, active) VALUES (?, ?, ?, ?, ?, TRUE)",
            UUID.randomUUID(), "Pessoa", email, "hash", "MEMBER");
    }
}
