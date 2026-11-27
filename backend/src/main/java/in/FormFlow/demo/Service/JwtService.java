
package in.FormFlow.demo.Service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expiration;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms:${jwt.expiration:86400000}}") long expiration) {

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Missing required JWT_SECRET configuration. Provide a Base64-encoded key with at least 256 bits of entropy."
            );
        }

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret.trim());
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "Invalid JWT_SECRET configuration. Value must be Base64-encoded.",
                    ex
            );
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "Weak JWT_SECRET configuration. Decoded key must be at least 256 bits (32 bytes)."
            );
        }

        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expiration = expiration;
    }

    public String generateToken(UUID userId, String email, String role) {

        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public Claims extractClaims(String token) {

        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
