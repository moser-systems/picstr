package io.picstr.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI description of the REST API, with API-key (bearer) authentication. */
@Configuration
public class OpenApiConfig {

    private static final String API_KEY = "apiKey";

    @Bean
    public OpenAPI picstrOpenApi() {
        return new OpenAPI()
                .info(new Info().title("PicStr API").version("v1")
                        .description("Photos, categories and tags. Authenticate with an API key from APP_API_KEYS."))
                .components(new Components().addSecuritySchemes(API_KEY, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer")
                        .description("Authorization: Bearer <API key>")))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY));
    }
}
