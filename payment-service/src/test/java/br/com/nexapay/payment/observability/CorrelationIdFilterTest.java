package br.com.nexapay.payment.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldPreserveValidIncomingCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "client-request-123");

        AtomicReference<String> valueInsideChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> valueInsideChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertEquals("client-request-123", response.getHeader(CorrelationIdFilter.HEADER_NAME));
        assertEquals("client-request-123", valueInsideChain.get());
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    @Test
    void shouldGenerateCorrelationIdWhenHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> valueInsideChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> valueInsideChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        String generated = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        assertEquals(generated, valueInsideChain.get());
        assertEquals(UUID.fromString(generated).toString(), generated);
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    @Test
    void shouldReplaceInvalidIncomingCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "invalid correlation id with spaces");

        AtomicReference<String> valueInsideChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> valueInsideChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        String generated = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        assertNotEquals("invalid correlation id with spaces", generated);
        assertTrue(generated != null && !generated.isBlank());
        assertEquals(generated, valueInsideChain.get());
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }
}
