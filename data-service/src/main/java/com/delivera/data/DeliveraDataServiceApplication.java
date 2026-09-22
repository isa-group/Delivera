package com.delivera.data;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import io.github.isagroup.spaceclient.spring.SpaceClientAutoConfiguration;

@SpringBootApplication
@Import(SpaceClientAutoConfiguration.class)
public class DeliveraDataServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(DeliveraDataServiceApplication.class, args);
	}

}
