package com.cc.ccojbackend.gateway.filter;

import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayRateLimitFilter implements GlobalFilter, Ordered {
    private static final byte[] BLOCKED = """
            {"code":429,"message":"请求过于频繁，请稍后重试","data":null}
            """.getBytes(StandardCharsets.UTF_8);
    private static final PathPattern PROBLEM_DETAIL =
            PathPatternParser.defaultInstance.parse("/problem/{id:[0-9]+}");
    private final boolean submitEnabled;

    public GatewayRateLimitFilter(
            @Value("${ccoj.protection.submit-limit-enabled:true}") boolean submitEnabled,
            @Value("${ccoj.protection.submit-qps:2}") int submitQps,
            @Value("${ccoj.protection.login-qps:5}") int loginQps,
            @Value("${ccoj.protection.register-qps:2}") int registerQps,
            @Value("${ccoj.protection.problem-query-qps:30}") int problemQueryQps) {
        this.submitEnabled = submitEnabled;
        FlowRuleManager.loadRules(List.of(
                rule("ccoj.submission.create", submitQps),
                rule("ccoj.user.login", loginQps),
                rule("ccoj.user.register", registerQps),
                rule("ccoj.problem.read", problemQueryQps)));
    }

    private static FlowRule rule(String resource, int qps) {
        if (qps < 1) throw new IllegalArgumentException(resource + " QPS must be positive");
        FlowRule rule = new FlowRule(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(qps);
        return rule;
    }

    private String resource(ServerWebExchange exchange) {
        var request = exchange.getRequest();
        var path = request.getPath().pathWithinApplication();
        if (request.getMethod() == HttpMethod.POST) {
            return switch (path.value()) {
                case "/submission/judge" -> submitEnabled ? "ccoj.submission.create" : null;
                case "/user/login" -> "ccoj.user.login";
                case "/user/register" -> "ccoj.user.register";
                default -> null;
            };
        }
        if (request.getMethod() == HttpMethod.GET
                && ("/problem".equals(path.value()) || PROBLEM_DETAIL.matches(path))) {
            return "ccoj.problem.read";
        }
        return null;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String resource = resource(exchange);
        if (resource != null) {
            // This entry measures admission only. WebFlux request processing is asynchronous.
            try {
                SphU.entry(resource, EntryType.IN).exit();
            } catch (BlockException blocked) {
                var response = exchange.getResponse();
                response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                response.getHeaders().set("Retry-After", "1");
                return response.writeWith(Mono.just(response.bufferFactory().wrap(BLOCKED)));
            }
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
