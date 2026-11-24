
package in.FormFlow.demo.config;

import in.FormFlow.demo.Service.JwtService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

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
        
        System.out.println("REQUEST: " + request.getMethod()
        + " " + request.getRequestURI());

        System.out.println("AUTH HEADER PRESENT: "
                + (authHeader != null));

        System.out.println("BEARER PREFIX PRESENT: "
                + (authHeader != null && authHeader.startsWith("Bearer ")));

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

                String authority = (role != null && role.startsWith("ROLE_")) ? role : "ROLE_" + role;
                var authorities = role != null ? List.of(new SimpleGrantedAuthority(authority)) : List.<SimpleGrantedAuthority>of();

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

            System.out.println("JWT VALIDATION FAILED!");
            System.out.println("Reason: " + ex.getMessage());

            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
