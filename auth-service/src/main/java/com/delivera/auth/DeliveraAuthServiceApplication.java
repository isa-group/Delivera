package com.delivera.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class DeliveraAuthServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(DeliveraAuthServiceApplication.class, args);
	}

}
