package com.cc.ccojbackend.gateway.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class GatewayRateLimitFilterTest {

    @AfterEach
    void clearRules() {
        FlowRuleManager.loadRules(List.of());
    }

    @Test
    void capsSubmissionsWithoutThrottlingResultPolling() {
        var filter = new GatewayRateLimitFilter(true, 1, 5, 2, 30);
        GatewayFilterChain downstream = exchange -> {
            exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return Mono.empty();
        };
        var first = MockServerWebExchange.from(MockServerHttpRequest.post("/submission/judge"));
        filter.filter(first, downstream).block(Duration.ofSeconds(1));
        assertEquals(HttpStatus.NO_CONTENT, first.getResponse().getStatusCode());

        var next = MockServerWebExchange.from(MockServerHttpRequest.post("/submission/judge"));
        filter.filter(next, downstream).block(Duration.ofSeconds(1));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, next.getResponse().getStatusCode());
        assertEquals("1", next.getResponse().getHeaders().getFirst("Retry-After"));

        var poll = MockServerWebExchange.from(MockServerHttpRequest.get("/submission/42"));
        filter.filter(poll, downstream).block(Duration.ofSeconds(1));
        assertEquals(HttpStatus.NO_CONTENT, poll.getResponse().getStatusCode());
    }
}
