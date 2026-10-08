package me.yuugao.pullhire.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** springdoc metadata for the generated auth-service OpenAPI spec ({@code docs/api.md §4}). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pullHireAuthOpenApi() {
        return new OpenAPI().info(new Info()
                .title("PullHire auth-service")
                .description("Идентичность, JWT RS256/JWKS, роли и согласия 152-ФЗ (docs/api.md §3.1).")
                .version("1"));
    }
}
