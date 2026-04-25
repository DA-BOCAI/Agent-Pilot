package com.hay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AgentCopilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentCopilotApplication.class, args);
    }

}
