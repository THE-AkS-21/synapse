package com.skaeht.synapse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableJpaRepositories(basePackages = "com.skaeht.synapse.repository")
public class SynapseApplication {

	public static void main(String[] args) {
		SpringApplication.run(SynapseApplication.class, args);
	}
}