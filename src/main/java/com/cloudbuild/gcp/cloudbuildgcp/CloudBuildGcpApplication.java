package com.cloudbuild.gcp.cloudbuildgcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@EnableWebMvc
@EnableJpaRepositories
@SpringBootApplication
public class CloudBuildGcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudBuildGcpApplication.class, args);
    }

}
