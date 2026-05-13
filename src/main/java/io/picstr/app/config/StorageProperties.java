package io.picstr.app.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    private String type = "s3";

    private Local local = new Local();
    private S3 s3 = new S3();
    private Ftp ftp = new Ftp();

    @Getter
    @Setter
    public static class Local {
        private String basePath = "./uploads";
    }

    @Getter
    @Setter
    public static class S3 {
        private String endpoint;
        private String region = "eu-central-1";
        private String bucket;
        private String accessKey;
        private String secretKey;
        private boolean pathStyleAccessEnabled = true;
    }

    @Getter
    @Setter
    public static class Ftp {
        private String username;
        private String password;
        private String host;
        private int port;
        private String protocol;
        private String basePath = "./uploads";
    }

}
