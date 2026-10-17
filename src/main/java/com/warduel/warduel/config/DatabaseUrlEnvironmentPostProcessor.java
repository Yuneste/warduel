package com.warduel.warduel.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Lets the app use a Postgres connection string as hosting providers hand it out
 * ({@code DATABASE_URL=postgresql://user:password@host:5432/db?sslmode=require}, e.g. from Neon),
 * by translating it into Spring's JDBC datasource properties. Without DATABASE_URL the
 * in-memory H2 database from application.properties is used.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String SOURCE_NAME = "databaseUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = environment.getProperty("DATABASE_URL");
        if(url == null || url.isBlank()) {
            return;
        }
        environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, toSpringProperties(url.trim())));
    }

    static Map<String, Object> toSpringProperties(String databaseUrl) {
        URI uri = URI.create(databaseUrl);
        String scheme = uri.getScheme();
        if(!"postgres".equals(scheme) && !"postgresql".equals(scheme)) {
            throw new IllegalArgumentException("DATABASE_URL must start with postgres:// or postgresql://");
        }

        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
        if(uri.getPort() > 0) {
            jdbc.append(':').append(uri.getPort());
        }
        jdbc.append(uri.getRawPath());
        if(uri.getRawQuery() != null) {
            jdbc.append('?').append(uri.getRawQuery());
        }

        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", jdbc.toString());
        props.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        String userInfo = uri.getRawUserInfo();
        if(userInfo != null) {
            int colon = userInfo.indexOf(':');
            props.put("spring.datasource.username", decode(colon < 0 ? userInfo : userInfo.substring(0, colon)));
            props.put("spring.datasource.password", colon < 0 ? "" : decode(userInfo.substring(colon + 1)));
        }
        return props;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
