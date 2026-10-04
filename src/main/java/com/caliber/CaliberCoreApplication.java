package com.caliber;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CaliberCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(CaliberCoreApplication.class, args);
    }
}
