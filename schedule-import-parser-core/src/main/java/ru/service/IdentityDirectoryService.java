package ru.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.dto.InternalUserDetailsDto;
import ru.exception.ResourceNotFoundException;

@Service
public class IdentityDirectoryService {

    private static final Logger log = LoggerFactory.getLogger(IdentityDirectoryService.class);

    private final RestClient restClient;

    public IdentityDirectoryService(@Value("${identity.service.url}") String identityServiceUrl,
                                    @Value("${identity.service.api-key}") String internalApiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(identityServiceUrl)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .build();
    }

    public InternalUserDetailsDto getByUsername(String username) {
        try {
            log.debug("Requesting user from identity-service: username={}", username);
            InternalUserDetailsDto user = restClient.get()
                    .uri("/internal/users/by-username/{username}", username)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new ResourceNotFoundException("User not found in identity-service: " + username);
                    })
                    .body(InternalUserDetailsDto.class);

            if (user == null) {
                log.warn("identity-service returned empty body for username={}", username);
                throw new ResourceNotFoundException("User not found in identity-service: " + username);
            }

            log.debug("User loaded from identity-service: username={}, userId={}", username, user.getId());
            return user;
        } catch (RestClientResponseException ex) {
            log.warn("Failed to load user from identity-service: username={}, status={}, message={}",
                    username, ex.getStatusCode(), ex.getMessage());
            throw new ResourceNotFoundException("Failed to load user from identity-service: " + username);
        }
    }
}
