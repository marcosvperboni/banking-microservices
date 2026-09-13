package com.marcosperboni.banking.gateway.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marcosperboni.banking.gateway.security.JwtValidator;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Minimal hand-rolled reactive reverse proxy: checks the bearer token for
 * protected paths, resolves the target service from {@link RouteResolver}
 * and forwards the request/response verbatim via WebClient.
 *
 * Replaces Spring Cloud Gateway, whose 2025.0.0 release train ships a
 * spring-cloud-commons jar that references a Spring Boot class relocated in
 * Boot 4.0.x (org.springframework.boot.web.context.WebServerInitializedEvent),
 * so it fails to boot against this project's Spring Boot 4.0.8 parent.
 */
@Component
public class ReverseProxyWebFilter implements WebFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> HOP_BY_HOP_REQUEST_HEADERS = Set.of("host", "content-length");
    private static final Set<String> HOP_BY_HOP_RESPONSE_HEADERS = Set.of("transfer-encoding", "content-length", "connection");
    private static final List<String> PUBLIC_PREFIXES = List.of("/api/v1/auth");
    private static final String ACTUATOR_PREFIX = "/actuator";

    private final RouteResolver routeResolver;
    private final JwtValidator jwtValidator;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public ReverseProxyWebFilter(RouteResolver routeResolver, JwtValidator jwtValidator, WebClient.Builder webClientBuilder) {
        this.routeResolver = routeResolver;
        this.jwtValidator = jwtValidator;
        this.webClient = webClientBuilder.build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getRawPath();

        if (path.startsWith(ACTUATOR_PREFIX)) {
            return chain.filter(exchange);
        }

        if (!isPublic(path) && !hasValidToken(request)) {
            return writeJson(exchange, HttpStatus.UNAUTHORIZED, "Token ausente, invalido ou expirado", path);
        }

        Optional<String> targetBase = routeResolver.resolve(path);
        if (targetBase.isEmpty()) {
            return writeJson(exchange, HttpStatus.NOT_FOUND, "Rota nao encontrada", path);
        }

        URI targetUri = UriComponentsBuilder.fromUriString(targetBase.get())
                .path(path)
                .query(request.getURI().getRawQuery())
                .build(true)
                .toUri();

        return proxy(exchange, targetUri);
    }

    private boolean isPublic(String path) {
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean hasValidToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return header != null && header.startsWith(BEARER_PREFIX) && jwtValidator.isValid(header.substring(BEARER_PREFIX.length()));
    }

    private Mono<Void> proxy(ServerWebExchange exchange, URI targetUri) {
        ServerHttpRequest request = exchange.getRequest();

        HttpHeaders forwardHeaders = new HttpHeaders();
        request.getHeaders().forEach((name, values) -> {
            if (!HOP_BY_HOP_REQUEST_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                forwardHeaders.addAll(name, values);
            }
        });

        return webClient.method(request.getMethod())
                .uri(targetUri)
                .headers(h -> h.addAll(forwardHeaders))
                .body(BodyInserters.fromDataBuffers(request.getBody()))
                .exchangeToMono(clientResponse -> {
                    exchange.getResponse().setStatusCode(clientResponse.statusCode());
                    clientResponse.headers().asHttpHeaders().forEach((name, values) -> {
                        if (!HOP_BY_HOP_RESPONSE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                            exchange.getResponse().getHeaders().addAll(name, values);
                        }
                    });
                    return exchange.getResponse().writeWith(clientResponse.bodyToFlux(DataBuffer.class));
                })
                .onErrorResume(ex -> writeJson(exchange, HttpStatus.BAD_GATEWAY,
                        "Servico indisponivel: " + ex.getMessage(), request.getURI().getRawPath()));
    }

    private Mono<Void> writeJson(ServerWebExchange exchange, HttpStatus status, String message, String path) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", path);
        body.put("fieldErrors", List.of());

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = ("{\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
