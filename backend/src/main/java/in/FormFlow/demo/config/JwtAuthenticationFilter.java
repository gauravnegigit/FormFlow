
package in.FormFlow.demo.config;

import in.FormFlow.demo.Service.JwtService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        
        String authHeader = request.getHeader("Authorization");
        String safeUri = sanitizeUri(request.getRequestURI());

        log.debug("Processing {} {}", request.getMethod(), safeUri);

        // No token: continue; SecurityConfig decides whether access is allowed
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                Claims claims = jwtService.extractClaims(token);

                String userId = claims.getSubject();
                String role = claims.get("role", String.class);

                List<SimpleGrantedAuthority> authorities = List.of();
                if (role != null && !role.isBlank()) {
                    String normalizedRole = role.trim();
                    String authority = normalizedRole.startsWith("ROLE_") ? normalizedRole : "ROLE_" + normalizedRole;
                    authorities = List.of(new SimpleGrantedAuthority(authority));
                }

                var authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                authorities
                        );

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext()
                        .setAuthentication(authentication);
            }

        } catch (JwtException | IllegalArgumentException ex) {

            log.warn("JWT authentication failed for {} {}: {}",
                    request.getMethod(),
                    safeUri,
                    ex.getClass().getSimpleName());

            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private String sanitizeUri(String uri) {
        if (uri == null) {
            return "";
        }
        if (uri.startsWith("/api/submissions/track/")) {
            return "/api/submissions/track/***";
        }
        return uri;
    }
}
