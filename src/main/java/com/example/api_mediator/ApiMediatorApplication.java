package com.example.api_mediator;

import com.example.api_mediator.config.BackendConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(BackendConfig.class)
public class ApiMediatorApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiMediatorApplication.class, args);
	}

}
