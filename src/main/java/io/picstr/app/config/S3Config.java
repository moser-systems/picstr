package io.picstr.app.config;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3Config {

    @Bean
    S3Client s3Client(StorageProperties properties) {
        var s3Props = properties.getS3();
        var builder = S3Client.builder()
                .region(Region.of(s3Props.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(s3Props.isPathStyleAccessEnabled())
                        .build())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3Props.getAccessKey(), s3Props.getSecretKey())));

        if (StringUtils.hasText(s3Props.getEndpoint())) {
            builder.endpointOverride(URI.create(s3Props.getEndpoint()));
        }

        return builder.build();
    }
}
