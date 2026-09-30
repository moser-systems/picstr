package io.picstr.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Thread pool for background processing of uploads (HEIC conversion, thumbnails). */
@Configuration
public class ProcessingConfig {

    @Bean
    public ThreadPoolTaskExecutor photoProcessingExecutor(@Value("${app.photo.processing.threads:2}") int threads) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("photo-processing-");
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        // Let running conversions finish on shutdown; anything still PROCESSING is picked up on the next start.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
