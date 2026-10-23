package com.work.converter.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SWF Audio Converter API")
                        .description("API reactiva para convertir archivos de audio entre formatos: MP3, WAV, OGG, AAC, FLAC, WMA")
                        .version("1.0.0"));
    }
}
