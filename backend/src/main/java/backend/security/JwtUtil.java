package backend.security;

import org.springframework.security.oauth2.jwt.Jwt;

public final class JwtUtil {
    private JwtUtil() {}

    public static Long userId(Jwt jwt) {
        Object value = jwt.getClaim("uid");
        return value == null ? null : ((Number) value).longValue();
    }
}