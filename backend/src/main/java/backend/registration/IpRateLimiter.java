package backend.registration;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class IpRateLimiter {

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public boolean allow(String key, int max, Duration window) {
        if (hits.size() > 10_000) hits.clear(); // simple safety valve against memory growth
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);
        Deque<Instant> queue = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst().isBefore(cutoff)) {
                queue.pollFirst();
            }
            if (queue.size() >= max) return false;
            queue.addLast(now);
            return true;
        }
    }
}