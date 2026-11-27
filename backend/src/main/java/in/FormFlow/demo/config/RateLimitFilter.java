package in.FormFlow.demo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final int CLEANUP_INTERVAL = 256;

    private final RateLimitProperties properties;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final AtomicInteger requestTick = new AtomicInteger(0);
    private final long maxWindowMs;

    public RateLimitFilter(RateLimitProperties properties) {
        this.properties = properties;
        this.maxWindowMs = Math.max(
                Math.max(properties.getLoginWindowSeconds(), properties.getRegisterWindowSeconds()),
                Math.max(properties.getSubmitWindowSeconds(), properties.getTrackWindowSeconds())
        ) * 1000L;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (!properties.isEnabled() || HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitRule rule = matchRule(request);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long nowMs = System.currentTimeMillis();
        maybeEvictExpired(nowMs);

        String clientKey = resolveClientIp(request);
        String bucketKey = rule.bucketName() + ":" + clientKey;

        WindowCounter counter = counters.computeIfAbsent(bucketKey, k -> new WindowCounter());
        long retryAfterSeconds = counter.tryAcquire(nowMs, rule.windowMs(), rule.maxRequests());

        if (retryAfterSeconds > 0) {
            log.warn("Rate limit exceeded for endpoint={} clientIp={}", rule.bucketName(), clientKey);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\": 429, \"error\": \"Too Many Requests\", "
                            + "\"message\": \"Too many requests. Please try again later.\", "
                            + "\"retryAfterSeconds\": " + retryAfterSeconds + "}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private RateLimitRule matchRule(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
        }

        if (HttpMethod.POST.matches(method)) {
            if ("/api/auth/login".equals(path)) {
                return new RateLimitRule(
                        "AUTH_LOGIN",
                        properties.getLoginMaxRequests(),
                        properties.getLoginWindowSeconds() * 1000L
                );
            }
            if ("/api/auth/register".equals(path)) {
                return new RateLimitRule(
                        "AUTH_REGISTER",
                        properties.getRegisterMaxRequests(),
                        properties.getRegisterWindowSeconds() * 1000L
                );
            }
            if (path.startsWith("/api/submissions/public/") && path.length() > "/api/submissions/public/".length()) {
                return new RateLimitRule(
                        "SUBMISSION_PUBLIC",
                        properties.getSubmitMaxRequests(),
                        properties.getSubmitWindowSeconds() * 1000L
                );
            }
        } else if (HttpMethod.GET.matches(method)) {
            if (path.startsWith("/api/submissions/track/") && path.length() > "/api/submissions/track/".length()) {
                return new RateLimitRule(
                        "SUBMISSION_TRACK",
                        properties.getTrackMaxRequests(),
                        properties.getTrackWindowSeconds() * 1000L
                );
            }
        }

        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (properties.isTrustProxyHeaders()) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                int commaIdx = forwardedFor.indexOf(',');
                String firstIp = commaIdx >= 0 ? forwardedFor.substring(0, commaIdx) : forwardedFor;
                if (!firstIp.isBlank()) {
                    return firstIp.trim();
                }
            }
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "unknown";
    }

    private void maybeEvictExpired(long nowMs) {
        if (requestTick.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            counters.entrySet().removeIf(entry -> entry.getValue().isExpired(nowMs, maxWindowMs));
        }
    }

    private record RateLimitRule(String bucketName, int maxRequests, long windowMs) {}

    private static final class WindowCounter {
        private long windowStartMs;
        private int count;

        synchronized long tryAcquire(long nowMs, long windowMs, int maxRequests) {
            if (windowStartMs == 0L || nowMs - windowStartMs >= windowMs) {
                windowStartMs = nowMs;
                count = 1;
                return 0L;
            }
            if (count < maxRequests) {
                count++;
                return 0L;
            }
            long remainingMs = windowMs - (nowMs - windowStartMs);
            return Math.max(1L, (remainingMs + 999L) / 1000L);
        }

        synchronized boolean isExpired(long nowMs, long windowMs) {
            return windowStartMs > 0L && (nowMs - windowStartMs >= windowMs);
        }
    }
}
