package br.com.nexapay.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRouteDefinitionIntegrationTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    void shouldLoadExactlyTheFiveNexaPayRoutes() {
        var definitions = routeDefinitionLocator.getRouteDefinitions()
                .collectList()
                .block(Duration.ofSeconds(10));

        assertThat(definitions).isNotNull();

        Set<String> routeIds = definitions.stream()
                .map(RouteDefinition::getId)
                .collect(Collectors.toSet());

        assertThat(routeIds)
                .containsExactlyInAnyOrder(
                        "auth-service",
                        "payment-service",
                        "account-service",
                        "ledger-service",
                        "fraud-service"
                );
    }
}
