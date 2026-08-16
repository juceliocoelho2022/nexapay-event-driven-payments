package br.com.nexapay.payment;

import br.com.nexapay.payment.api.CreatePixPaymentRequest;
import br.com.nexapay.payment.api.PaymentResponse;
import br.com.nexapay.payment.repository.OutboxEventRepository;
import br.com.nexapay.payment.repository.PaymentRepository;
import br.com.nexapay.payment.service.PaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = "nexapay.outbox.fixed-delay-ms=600000")
class PaymentIdempotencyConcurrencyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("nexapay_payment_concurrency_test")
            .withUsername("nexapay")
            .withPassword("nexapay");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        paymentRepository.deleteAll();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void shouldCreateExactlyOnePaymentAndOutboxForConcurrentSameKey() throws Exception {
        String idempotencyKey = "concurrent-payment-001";
        CreatePixPaymentRequest request = new CreatePixPaymentRequest(
                "ACC-CONCURRENT-001",
                "concurrent@nexapay.test",
                new BigDecimal("250.00"),
                "Concurrent PIX"
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<PaymentResponse>> futures = List.of(
                submit(idempotencyKey, request, ready, start),
                submit(idempotencyKey, request, ready, start)
        );

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        PaymentResponse first = futures.get(0).get(30, TimeUnit.SECONDS);
        PaymentResponse second = futures.get(1).get(30, TimeUnit.SECONDS);

        assertThat(first.id()).isEqualTo(second.id());
        assertThat(paymentRepository.findByIdempotencyKey(idempotencyKey)).isPresent();
        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    private Future<PaymentResponse> submit(
            String idempotencyKey,
            CreatePixPaymentRequest request,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return executor.submit(() -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent test start latch timed out");
            }
            return paymentService.createPixPayment(idempotencyKey, request);
        });
    }
}
