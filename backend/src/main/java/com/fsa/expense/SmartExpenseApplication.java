package com.fsa.expense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class SmartExpenseApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartExpenseApplication.class, args);
    }
}
