package com.digiplus.loganalyzer.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * JSON-based login/logout/status endpoints for the React SPA.
 * Session cookie is set automatically by Spring Security on successful login.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;

    public AuthController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    /** Authenticate with username + password; returns 200 on success, 401 on failure. */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest body, HttpServletRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(body.username(), body.password())
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        // Persist the SecurityContext in the session so subsequent requests are authenticated
        HttpSession session = request.getSession(true);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext()
        );
        return Map.of(
                "username", auth.getName(),
                "authenticated", true
        );
    }

    /** Check whether the current session is authenticated. */
    @GetMapping("/me")
    public Map<String, Object> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean logged = auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal());
        if (!logged) {
            return Map.of("authenticated", false);
        }
        return Map.of("username", auth.getName(), "authenticated", true);
    }

    public record LoginRequest(String username, String password) { }
}
