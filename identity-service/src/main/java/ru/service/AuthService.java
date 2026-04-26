package ru.service;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import ru.dto.LoginRequest;
import ru.dto.TokenResponse;
import ru.model.User;
import ru.repository.UserRepository;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final UserLoginTrackingService loginTrackingService;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       JwtService jwtService,
                       UserLoginTrackingService loginTrackingService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.loginTrackingService = loginTrackingService;
    }

    public TokenResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new BadCredentialsException("User not found after authentication"));

        String accessToken = jwtService.generateAccessToken(user);
        log.info("JWT issued: username={}, role={}", user.getUsername(), user.getRole());

        loginTrackingService.recordLogin(user, httpRequest);

        return TokenResponse.builder()
                .tokenType("Bearer")
                .accessToken(accessToken)
                .expiresAt(jwtService.calculateExpiryFromNow())
                .build();
    }
}
