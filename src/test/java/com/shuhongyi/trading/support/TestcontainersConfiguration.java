package com.shuhongyi.trading.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shares a single PostgreSQL 16 container across every test context in the JVM.
 * The container is started once and stopped by Testcontainers' Ryuk reaper.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    static {
        POSTGRES.start();
    }

    @Bean(destroyMethod = "")
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return POSTGRES;
    }
}
