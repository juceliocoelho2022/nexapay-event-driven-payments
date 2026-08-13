package br.com.nexapay.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class NexaPayPaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexaPayPaymentApplication.class, args);
    }
}
