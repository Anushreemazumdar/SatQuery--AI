package com.satquery.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI satQueryOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SatQuery AI Backend API")
                .version("1.0.0")
                .description("Natural-language satellite imagery analysis. This API orchestrates projects, images, "
                        + "analyses and reports and delegates AI inference to a separate Python AI service."));
    }
}
