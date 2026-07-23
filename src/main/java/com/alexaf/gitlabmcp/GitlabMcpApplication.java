package com.alexaf.gitlabmcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GitlabMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(GitlabMcpApplication.class, args);
    }
}
