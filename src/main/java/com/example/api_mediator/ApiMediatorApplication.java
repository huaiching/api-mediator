package com.example.api_mediator;

import com.example.api_mediator.config.properties.ProxyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ProxyProperties.class})
public class ApiMediatorApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiMediatorApplication.class, args);
	}

}
