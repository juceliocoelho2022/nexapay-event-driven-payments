package br.com.nexapay.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.security.Principal;

@Configuration
public class GatewayRateLimitConfig {

    @Bean
    KeyResolver gatewayKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .switchIfEmpty(Mono.fromSupplier(() -> clientAddress(exchange.getRequest().getRemoteAddress())));
    }

    private String clientAddress(InetSocketAddress remoteAddress) {
        if (remoteAddress == null) {
            return "anonymous";
        }

        if (remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        return remoteAddress.getHostString();
    }
}
