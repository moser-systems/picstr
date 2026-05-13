package io.picstr.app.config;

import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local")
public class LocalUploadWebConfig implements WebMvcConfigurer {

    private final StorageProperties storageProperties;

    public LocalUploadWebConfig(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        var uploadRoot = Path.of(storageProperties.getLocal().getBasePath()).toAbsolutePath().normalize();
        log.info("Configuring local upload resource handler with root: {}", uploadRoot);
        var location = uploadRoot.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }

        registry.addResourceHandler("/assets/**")
                .addResourceLocations(location);
    }
}
