package in.FormFlow.demo.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class RateLimitProperties {

    private final boolean enabled;
    private final boolean trustProxyHeaders;

    private final int loginMaxRequests;
    private final long loginWindowSeconds;

    private final int registerMaxRequests;
    private final long registerWindowSeconds;

    private final int submitMaxRequests;
    private final long submitWindowSeconds;

    private final int trackMaxRequests;
    private final long trackWindowSeconds;

    public RateLimitProperties(
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.trust-proxy-headers:false}") boolean trustProxyHeaders,
            @Value("${app.rate-limit.login.max-requests:10}") int loginMaxRequests,
            @Value("${app.rate-limit.login.window-seconds:60}") long loginWindowSeconds,
            @Value("${app.rate-limit.register.max-requests:5}") int registerMaxRequests,
            @Value("${app.rate-limit.register.window-seconds:300}") long registerWindowSeconds,
            @Value("${app.rate-limit.submit.max-requests:20}") int submitMaxRequests,
            @Value("${app.rate-limit.submit.window-seconds:60}") long submitWindowSeconds,
            @Value("${app.rate-limit.track.max-requests:30}") int trackMaxRequests,
            @Value("${app.rate-limit.track.window-seconds:60}") long trackWindowSeconds) {

        this.enabled = enabled;
        this.trustProxyHeaders = trustProxyHeaders;
        this.loginMaxRequests = Math.max(1, loginMaxRequests);
        this.loginWindowSeconds = Math.max(1L, loginWindowSeconds);
        this.registerMaxRequests = Math.max(1, registerMaxRequests);
        this.registerWindowSeconds = Math.max(1L, registerWindowSeconds);
        this.submitMaxRequests = Math.max(1, submitMaxRequests);
        this.submitWindowSeconds = Math.max(1L, submitWindowSeconds);
        this.trackMaxRequests = Math.max(1, trackMaxRequests);
        this.trackWindowSeconds = Math.max(1L, trackWindowSeconds);
    }
}
