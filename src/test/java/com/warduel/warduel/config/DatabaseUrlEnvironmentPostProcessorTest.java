package com.warduel.warduel.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseUrlEnvironmentPostProcessorTest {

    @Test
    void translatesANeonStyleConnectionString() {
        Map<String, Object> props = DatabaseUrlEnvironmentPostProcessor.toSpringProperties(
                "postgresql://mathwars_owner:s3cr%3At@ep-cool-sun-123.eu-central-1.aws.neon.tech/mathwars?sslmode=require&channel_binding=require");

        assertThat(props).containsEntry("spring.datasource.url",
                "jdbc:postgresql://ep-cool-sun-123.eu-central-1.aws.neon.tech/mathwars?sslmode=require&channel_binding=require");
        assertThat(props).containsEntry("spring.datasource.username", "mathwars_owner");
        assertThat(props).containsEntry("spring.datasource.password", "s3cr:t"); // percent-encoding undone
        assertThat(props).containsEntry("spring.datasource.driver-class-name", "org.postgresql.Driver");
    }

    @Test
    void keepsAnExplicitPort() {
        assertThat(DatabaseUrlEnvironmentPostProcessor.toSpringProperties("postgres://u:p@localhost:5433/db"))
                .containsEntry("spring.datasource.url", "jdbc:postgresql://localhost:5433/db");
    }

    @Test
    void overridesTheH2DefaultsOnlyWhenDatabaseUrlIsSet() {
        MockEnvironment env = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:h2:mem:warduel");
        new DatabaseUrlEnvironmentPostProcessor().postProcessEnvironment(env, null);
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:h2:mem:warduel");

        env.setProperty("DATABASE_URL", "postgresql://u:p@db.example/app");
        new DatabaseUrlEnvironmentPostProcessor().postProcessEnvironment(env, null);
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://db.example/app");
    }

    @Test
    void rejectsOtherDatabases() {
        assertThatThrownBy(() -> DatabaseUrlEnvironmentPostProcessor.toSpringProperties("mysql://u:p@host/db"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
