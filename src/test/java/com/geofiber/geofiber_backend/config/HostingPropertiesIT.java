package com.geofiber.geofiber_backend.config;

import com.geofiber.geofiber_backend.support.IntegrationTest;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hospedagem gratuita (docs/PUBLICAR-GRATUITO.md): o Render injeta a porta em {@code PORT} e o pooler
 * do Supabase limita conexões ({@code DB_POOL_SIZE}). Sem essas variáveis, valem os padrões locais.
 */
class HostingPropertiesIT extends IntegrationTest {
    @Autowired
    private Environment env;

    @Autowired
    private DataSource dataSource;

    @Test
    void usesLocalDefaultsWithoutHostingVariables() {
        assertThat(env.getProperty("server.port")).isEqualTo("8080");
        assertThat(((HikariDataSource) dataSource).getMaximumPoolSize()).isEqualTo(10);
    }

    @Nested
    @TestPropertySource(properties = {"PORT=9123", "DB_POOL_SIZE=3"})
    class WithHostingVariables {
        @Autowired
        private Environment env;

        @Autowired
        private DataSource dataSource;

        @Test
        void readsPortAndPoolSizeFromTheEnvironment() {
            assertThat(env.getProperty("server.port")).isEqualTo("9123");
            assertThat(((HikariDataSource) dataSource).getMaximumPoolSize()).isEqualTo(3);
        }
    }
}
