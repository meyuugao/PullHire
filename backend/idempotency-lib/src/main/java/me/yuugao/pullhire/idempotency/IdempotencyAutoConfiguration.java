package me.yuugao.pullhire.idempotency;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Spring Boot auto-configuration for idempotency-lib: a service only has to
 * add the dependency and opt in via {@code pullhire.idempotency.enabled=true}
 * to get the Idempotency-Key filter with a JDBC result store
 * (docs/api.md §1–§2).
 *
 * <p>Registered through
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * Every bean is conditional, so a service may override any of them.
 */
@AutoConfiguration
@ConditionalOnClass(JdbcTemplate.class)
@EnableConfigurationProperties(IdempotencyProperties.class)
@ConditionalOnProperty(prefix = "pullhire.idempotency", name = "enabled", havingValue = "true")
public class IdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyResultStore idempotencyResultStore(JdbcTemplate jdbcTemplate,
                                                        IdempotencyProperties properties) {
        return new IdempotencyResultStore(jdbcTemplate, properties.getTtl());
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyFilter idempotencyFilter(IdempotencyResultStore store,
                                              IdempotencyProperties properties) {
        return new IdempotencyFilter(store, properties.getPathPatterns());
    }
}
