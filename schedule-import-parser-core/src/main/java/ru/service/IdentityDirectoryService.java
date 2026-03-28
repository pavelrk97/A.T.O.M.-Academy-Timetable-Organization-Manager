package ru.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.dto.InternalUserDetailsDto;
import ru.exception.ResourceNotFoundException;

@Service
public class IdentityDirectoryService {

    private final RestClient restClient;

    public IdentityDirectoryService(@Value("${identity.service.url}") String identityServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(identityServiceUrl)
                .build();
    }

    public InternalUserDetailsDto getByUsername(String username) {
        try {
            InternalUserDetailsDto user = restClient.get()
                    .uri("/internal/users/by-username/{username}", username)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new ResourceNotFoundException("User not found in identity-service: " + username);
                    })
                    .body(InternalUserDetailsDto.class);

            if (user == null) {
                throw new ResourceNotFoundException("User not found in identity-service: " + username);
            }

            return user;
        } catch (RestClientResponseException ex) {
            throw new ResourceNotFoundException("Failed to load user from identity-service: " + username);
        }
    }
}
