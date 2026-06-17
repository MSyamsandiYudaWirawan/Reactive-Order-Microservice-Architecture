package com.MSyamsandiYW.order_service;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@PostConstruct
	void setTimezone() {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Jakarta"));
	}
}
