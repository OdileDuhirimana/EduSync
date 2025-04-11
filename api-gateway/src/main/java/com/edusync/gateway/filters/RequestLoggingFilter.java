package com.edusync.gateway.filters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.UUID;

@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        String id = UUID.randomUUID().toString();
        InetSocketAddress remote = req.getRemoteAddress();
        log.info("GW_REQ id={} method={} path={} ip={} at={}", id, req.getMethod(), req.getURI().getPath(),
                remote != null ? remote.getAddress().getHostAddress() : "-", Instant.now());
        return chain.filter(exchange).doOnSuccess(aVoid -> {
            int status = exchange.getResponse().getStatusCode() != null ? exchange.getResponse().getStatusCode().value() : 0;
            log.info("GW_RES id={} status={} path={} at={}", id, status, req.getURI().getPath(), Instant.now());
        });
    }

    @Override
    public int getOrder() {
        return -1; // before NettyWriteResponseFilter
        }
}
