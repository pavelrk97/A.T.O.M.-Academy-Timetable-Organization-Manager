package ru.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class DownstreamAuthHeaderFactory {

    public String bearerHeader(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
            throw new IllegalStateException("JWT authentication is required to relay downstream authorization");
        }
        return "Bearer " + jwtAuthenticationToken.getToken().getTokenValue();
    }
}
