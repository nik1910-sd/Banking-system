package app.banking.apigatewayservice.filter;

import app.banking.apigatewayservice.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    @Autowired
    private RouteValidator validator;

    @Autowired
    private JwtUtil jwtUtil;

    public AuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return ((exchange, chain) -> {
            if (validator.isSecured.test(exchange.getRequest())) {
                if (!exchange.getRequest().getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }

                String authHeader = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION).get(0);
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    authHeader = authHeader.substring(7);
                }
                
                try {
                    jwtUtil.validateToken(authHeader);
                    
                    io.jsonwebtoken.Claims claims = jwtUtil.getAllClaimsFromToken(authHeader);
                    String email = claims.getSubject();
                    String role = claims.get("role", String.class);

                    // Check if the endpoint requires ADMIN role
                    if (exchange.getRequest().getURI().getPath().contains("/admin")) {
                        if (role == null || (!role.equals("ADMIN") && !role.equals("ROLE_ADMIN"))) {
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                            return exchange.getResponse().setComplete();
                        }
                    }

                    ServerHttpRequest request = exchange.getRequest()
                            .mutate()
                            .header("X-User-Email", email)
                            .header("X-User-Role", role != null ? role : "USER")
                            .build();
                            
                    return chain.filter(exchange.mutate().request(request).build());
                    
                } catch (Exception e) {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }
            }
            return chain.filter(exchange);
        });
    }

    public static class Config {
    }
}
