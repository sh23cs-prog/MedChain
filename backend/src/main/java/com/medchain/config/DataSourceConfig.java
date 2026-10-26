package com.medchain.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
@Profile("prod")
@Slf4j
public class DataSourceConfig {

    @Value("${spring.datasource.url:${DATABASE_URL:}}")
    private String dbUrl;

    @Value("${spring.datasource.username:${DATABASE_USER:}}")
    private String dbUser;

    @Value("${spring.datasource.password:${DATABASE_PASSWORD:}}")
    private String dbPassword;

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();

        if (dbUrl != null && (dbUrl.startsWith("postgres://") || dbUrl.startsWith("postgresql://"))) {
            try {
                URI uri = new URI(dbUrl);
                String host = uri.getHost();
                int port = uri.getPort() == -1 ? 5432 : uri.getPort();
                String path = uri.getPath();
                String query = uri.getQuery();

                String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path + (query != null ? "?" + query : "");
                config.setJdbcUrl(jdbcUrl);

                if (uri.getUserInfo() != null) {
                    String[] parts = uri.getUserInfo().split(":");
                    config.setUsername(parts[0]);
                    if (parts.length > 1) {
                        config.setPassword(parts[1]);
                    }
                }
                log.info("Configured PostgreSQL JDBC datasource from URI host: {}:{}", host, port);
            } catch (Exception e) {
                log.warn("Failed to parse PostgreSQL URI, falling back to raw URL: {}", e.getMessage());
                config.setJdbcUrl(dbUrl);
            }
        } else {
            config.setJdbcUrl(dbUrl == null || dbUrl.isEmpty() ? "jdbc:postgresql://postgres:5432/medchaindb" : dbUrl);
        }

        if (config.getUsername() == null && dbUser != null && !dbUser.isEmpty()) {
            config.setUsername(dbUser);
        }
        if (config.getPassword() == null && dbPassword != null && !dbPassword.isEmpty()) {
            config.setPassword(dbPassword);
        }

        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(300000);

        return new HikariDataSource(config);
    }
}
