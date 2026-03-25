package ru;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class ScheduleApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScheduleApiApplication.class, args);
    }
}