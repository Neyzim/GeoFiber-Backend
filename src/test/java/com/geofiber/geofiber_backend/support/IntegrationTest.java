package com.geofiber.geofiber_backend.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import com.geofiber.geofiber_backend.enums.user.UserRoleEnum;
import com.geofiber.geofiber_backend.security.JwtService;
import com.geofiber.geofiber_backend.security.LoginAttemptLimiter;
import com.geofiber.geofiber_backend.services.auth.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

/** Base dos testes de integração: PostGIS real em container, MockMvc e banco limpo a cada teste. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgisContainerConfig.class)
@TestPropertySource(properties = {
    "geofiber.bootstrap.admin.email=admin@teste.com",
    "geofiber.bootstrap.admin.password=senha-de-teste-123",
    "geofiber.security.jwt-secret=segredo-de-teste-com-mais-de-trinta-e-dois-bytes",
    "API_DOCS_ENABLED=true"
})
public abstract class IntegrationTest {
    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private LoginAttemptLimiter loginAttemptLimiter;

    /** Token de um administrador criado para cada teste; o banco é limpo depois, então recriamos sempre. */
    protected String token;

    protected NetworkFixtures fx;

    @BeforeEach
    void createFixtures() {
        // O limite de tentativas de login vive no contexto compartilhado entre os testes.
        loginAttemptLimiter.clear();
        UUID adminId = userService.create("Administradora de teste", "admin-teste@teste.com",
            "senha-admin-teste-123", UserRoleEnum.ADMIN).getId();
        token = jwtService.issue(adminId, UserRoleEnum.ADMIN);
        fx = new NetworkFixtures(mvc, token);
    }

    @AfterEach
    void cleanDatabase() {
        List<String> tables = jdbc.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = 'public' "
                + "AND tablename NOT IN ('flyway_schema_history', 'spatial_ref_sys')",
            String.class);
        if (!tables.isEmpty()) {
            jdbc.execute("TRUNCATE TABLE " + String.join(", ", tables) + " CASCADE");
        }
    }
}
