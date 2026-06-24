package com.bank.security;

import com.bank.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Login rate limiter with Redis-backed distributed counters and an in-memory fallback.
 *
 * <p>When {@code app.security.rate-limit.redis-enabled=true} (set in Docker deployments) and a
 * {@link StringRedisTemplate} is available, each counter is an atomic Redis INCR key with a TTL.
 * If the Redis operation fails (network error, Redis down), the limiter transparently falls back
 * to the in-memory fixed-window maps so the login endpoint remains available.
 *
 * <p>When Redis is disabled (default for dev/test), only the in-memory maps are used.
 */
@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    /**
     * Atomic increment + set-expiry-on-first-write Lua script.
     * Returns the count after incrementing; TTL is set only on the first write (count == 1)
     * to avoid resetting the window on every request.
     */
    private static final RedisScript<Long> INCR_SCRIPT = RedisScript.of(
            "local c = redis.call('INCR', KEYS[1])\n" +
            "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n" +
            "return c",
            Long.class);

    private final int maxIpAttemptsPerWindow;
    private final Duration ipWindowDuration;
    private final int maxUsernameAttemptsPerWindow;
    private final Duration usernameWindowDuration;
    private final boolean redisEnabled;

    // In-memory fallback maps (also the sole storage when redisEnabled=false)
    private final ConcurrentHashMap<String, Window> ipWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Window> usernameWindows = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    public LoginRateLimiter(
            @Value("${app.security.rate-limit.ip-max-attempts:20}") int maxIpAttemptsPerWindow,
            @Value("${app.security.rate-limit.ip-window-seconds:60}") long ipWindowSeconds,
            @Value("${app.security.rate-limit.username-max-attempts:10}") int maxUsernameAttemptsPerWindow,
            @Value("${app.security.rate-limit.username-window-seconds:900}") long usernameWindowSeconds,
            @Value("${app.security.rate-limit.redis-enabled:false}") boolean redisEnabled) {
        this.maxIpAttemptsPerWindow = maxIpAttemptsPerWindow;
        this.ipWindowDuration = Duration.ofSeconds(ipWindowSeconds);
        this.maxUsernameAttemptsPerWindow = maxUsernameAttemptsPerWindow;
        this.usernameWindowDuration = Duration.ofSeconds(usernameWindowSeconds);
        this.redisEnabled = redisEnabled;
    }

    /**
     * Checks and records a login attempt. Throws {@link RateLimitExceededException} if either
     * the per-IP or per-username limit is exceeded.
     */
    public void checkAndRecord(String ip, String username) {
        if (redisEnabled && redisTemplate != null) {
            try {
                checkWithRedis(ip, username);
                return;
            } catch (RateLimitExceededException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Redis rate limiter unavailable, falling back to in-memory: {}", e.getMessage());
            }
        }
        checkInMemory(ip, username);
    }

    /** Resets both counters after a successful login. */
    public void onLoginSuccess(String ip, String username) {
        if (redisEnabled && redisTemplate != null) {
            try {
                redisTemplate.delete("rate_limit:ip:" + ip);
                redisTemplate.delete("rate_limit:user:" + username);
                return;
            } catch (Exception e) {
                log.warn("Redis unavailable during rate-limit reset: {}", e.getMessage());
            }
        }
        ipWindows.remove(ip);
        usernameWindows.remove(username);
    }

    // ── Redis implementation ─────────────────────────────────────────────────

    private void checkWithRedis(String ip, String username) {
        Long ipCount = redisTemplate.execute(
                INCR_SCRIPT,
                List.of("rate_limit:ip:" + ip),
                String.valueOf(ipWindowDuration.getSeconds()));
        if (ipCount != null && ipCount > maxIpAttemptsPerWindow) {
            throw new RateLimitExceededException(
                    "Too many login attempts from this IP. Try again later.");
        }

        Long userCount = redisTemplate.execute(
                INCR_SCRIPT,
                List.of("rate_limit:user:" + username),
                String.valueOf(usernameWindowDuration.getSeconds()));
        if (userCount != null && userCount > maxUsernameAttemptsPerWindow) {
            throw new RateLimitExceededException(
                    "Too many login attempts for this account. Try again later.");
        }
    }

    // ── In-memory implementation ─────────────────────────────────────────────

    private void checkInMemory(String ip, String username) {
        if (!allow(ipWindows, ip, maxIpAttemptsPerWindow, ipWindowDuration)) {
            throw new RateLimitExceededException(
                    "Too many login attempts from this IP. Try again later.");
        }
        if (!allow(usernameWindows, username, maxUsernameAttemptsPerWindow, usernameWindowDuration)) {
            throw new RateLimitExceededException(
                    "Too many login attempts for this account. Try again later.");
        }
    }

    private boolean allow(ConcurrentHashMap<String, Window> map, String key,
                          int maxRequests, Duration window) {
        Instant now = Instant.now();
        Window w = map.compute(key, (k, existing) -> {
            if (existing == null || now.isAfter(existing.resetAt)) {
                return new Window(new AtomicInteger(0), now.plus(window));
            }
            return existing;
        });
        return w.count.incrementAndGet() <= maxRequests;
    }

    private record Window(AtomicInteger count, Instant resetAt) {}
}
