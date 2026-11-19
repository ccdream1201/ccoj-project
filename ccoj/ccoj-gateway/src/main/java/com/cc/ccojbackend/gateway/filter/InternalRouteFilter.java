package com.cc.ccojbackend.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class InternalRouteFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        boolean internal = request.getPath().elements().stream()
                .anyMatch(part -> part instanceof org.springframework.http.server.PathContainer.PathSegment segment
                        && "inner".equals(segment.valueToMatch()));
        if (internal) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }
        var clean = request.mutate().headers(headers -> headers.remove("X-CCOJ-Internal-Key")).build();
        return chain.filter(exchange.mutate().request(clean).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
