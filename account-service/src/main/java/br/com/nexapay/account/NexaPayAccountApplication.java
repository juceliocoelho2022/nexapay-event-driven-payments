package br.com.nexapay.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class NexaPayAccountApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexaPayAccountApplication.class, args);
    }
}
