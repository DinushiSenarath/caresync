package backend.security;

import backend.user.Role;
import org.springframework.security.oauth2.jwt.Jwt;

public final class JwtUtil {
    private JwtUtil() {}

    public static Long userId(Jwt jwt) {
        Object value = jwt.getClaim("uid");
        return value == null ? null : ((Number) value).longValue();
    }

    public static Role role(Jwt jwt) {
        String r = jwt.getClaimAsString("role");
        return r == null ? null : Role.valueOf(r);
    }
}