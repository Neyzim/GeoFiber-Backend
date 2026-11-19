package com.geofiber.geofiber_backend.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

/** Base dos testes de integração: PostGIS real em container, MockMvc e banco limpo a cada teste. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgisContainerConfig.class)
public abstract class IntegrationTest {
    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    protected NetworkFixtures fx;

    @BeforeEach
    void createFixtures() {
        fx = new NetworkFixtures(mvc);
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
