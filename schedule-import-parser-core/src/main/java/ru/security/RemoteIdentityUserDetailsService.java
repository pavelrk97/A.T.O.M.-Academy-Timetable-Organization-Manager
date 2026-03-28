package ru.security;

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

    private final RestClient restClient;

    public RemoteIdentityUserDetailsService(@Value("${identity.service.url}") String identityServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(identityServiceUrl)
                .build();
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            InternalUserDetailsDto user = restClient.get()
                    .uri("/internal/users/by-username/{username}", username)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new UsernameNotFoundException("User not found: " + username);
                    })
                    .body(InternalUserDetailsDto.class);

            if (user == null) {
                throw new UsernameNotFoundException("User not found: " + username);
            }

            return org.springframework.security.core.userdetails.User.builder()
                    .username(user.getUsername())
                    .password(user.getPassword())
                    .disabled(!user.isActive())
                    .roles(user.getRole().name())
                    .build();
        } catch (RestClientResponseException ex) {
            throw new UsernameNotFoundException("Failed to load user from identity-service: " + username, ex);
        }
    }
}
