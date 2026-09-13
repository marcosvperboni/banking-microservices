package com.marcosperboni.banking.gateway.routing;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RouteResolver {

    private final GatewayProperties properties;

    public RouteResolver(GatewayProperties properties) {
        this.properties = properties;
    }

    public Optional<String> resolve(String path) {
        return properties.getRoutes().stream()
                .filter(route -> path.startsWith(route.prefix()))
                .map(GatewayProperties.Route::uri)
                .findFirst();
    }
}
