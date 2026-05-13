package io.picstr.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PicstrApplication {

    public static void main(String[] args) {
        SpringApplication.run(PicstrApplication.class, args);
    }
}
