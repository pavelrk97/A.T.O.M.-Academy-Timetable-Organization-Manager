package ru.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.dto.InternalUserDetailsDto;

@Service
public class RemoteIdentityUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(RemoteIdentityUserDetailsService.class);

    private final RestClient restClient;

    public RemoteIdentityUserDetailsService(@Value("${identity.service.url}") String identityServiceUrl,
                                            @Value("${identity.service.api-key}") String internalApiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(identityServiceUrl)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .build();
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            log.debug("Authenticating user via identity-service: username={}", username);
            InternalUserDetailsDto user = restClient.get()
                    .uri("/internal/users/by-username/{username}", username)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new UsernameNotFoundException("User not found: " + username);
                    })
                    .body(InternalUserDetailsDto.class);

            if (user == null) {
                log.warn("identity-service returned empty auth payload for username={}", username);
                throw new UsernameNotFoundException("User not found: " + username);
            }

            log.debug("User authenticated via identity-service: username={}, active={}, role={}",
                    user.getUsername(), user.isActive(), user.getRole());
            return org.springframework.security.core.userdetails.User.builder()
                    .username(user.getUsername())
                    .password(user.getPassword())
                    .disabled(!user.isActive())
                    .roles(user.getRole().name())
                    .build();
        } catch (RestClientResponseException ex) {
            log.warn("Failed auth lookup in identity-service: username={}, status={}, message={}",
                    username, ex.getStatusCode(), ex.getMessage());
            throw new UsernameNotFoundException("Failed to load user from identity-service: " + username, ex);
        }
    }
}
