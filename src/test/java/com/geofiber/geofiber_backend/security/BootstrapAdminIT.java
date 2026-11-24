package com.geofiber.geofiber_backend.security;

import com.geofiber.geofiber_backend.services.auth.BootstrapAdminRunner;
import com.geofiber.geofiber_backend.services.auth.UserService;
import com.geofiber.geofiber_backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapAdminIT extends IntegrationTest {
    private static final String ADMIN_EMAIL = "admin@teste.com";
    private static final String ADMIN_PASSWORD = "senha-de-teste-123";

    @Autowired
    private BootstrapAdminRunner runner;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserService userService;

    @Test
    void bootstrapCreatesAdminWhenNoUsersAndStoresHashedPassword() throws Exception {
        jdbc.execute("TRUNCATE app_user CASCADE");

        runner.run(null);

        assertEquals("ADMIN", jdbc.queryForObject("SELECT role FROM app_user WHERE email = ?", String.class, ADMIN_EMAIL));
        String stored = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE email = ?", String.class, ADMIN_EMAIL);
        assertNotEquals(ADMIN_PASSWORD, stored);
        assertTrue(passwordEncoder.matches(ADMIN_PASSWORD, stored));
    }

    @Test
    void legacyRowsAreAdoptedByBootstrapAdmin() {
        jdbc.execute("TRUNCATE app_user CASCADE");
        jdbc.update("INSERT INTO network_olt (id, name, usage_status, geom) VALUES (?, 'OLT-antiga', 'PLANNED', "
            + "ST_SetSRID(ST_MakePoint(-46.6, -23.5), 4326))", UUID.randomUUID());

        runner.run(null);

        UUID admin = jdbc.queryForObject("SELECT id FROM app_user WHERE email = ?", UUID.class, ADMIN_EMAIL);
        UUID owner = jdbc.queryForObject("SELECT created_by FROM network_olt WHERE name = 'OLT-antiga'", UUID.class);
        assertEquals(admin, owner);
    }

    @Test
    void missingBootstrapConfigFailsWhenThereAreNoUsers() {
        jdbc.execute("TRUNCATE app_user CASCADE");
        BootstrapAdminRunner unconfigured = new BootstrapAdminRunner(userService, "", "", "Administrador");

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> unconfigured.run(null));
        assertTrue(error.getMessage().contains("ADMIN_EMAIL"));
    }
}
