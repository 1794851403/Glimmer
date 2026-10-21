package com.glimmer.common.auth;

import com.glimmer.common.web.LoginUser;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void shouldRoundTripUserIdAndRole() {
        JwtService service = new JwtService(new JwtProperties(SECRET, Duration.ofDays(7)));
        service.init();

        String token = service.generate(42L, "ADMIN");
        LoginUser user = service.parse(token);

        assertThat(user.userId()).isEqualTo(42L);
        assertThat(user.role()).isEqualTo("ADMIN");
        assertThat(user.isAdmin()).isTrue();
    }

    @Test
    void shouldRejectExpiredToken() {
        JwtService service = new JwtService(new JwtProperties(SECRET, Duration.ofSeconds(-1)));
        service.init();
        String token = service.generate(1L, "USER");

        assertThatThrownBy(() -> service.parse(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void shouldRejectShortSecret() {
        JwtService service = new JwtService(new JwtProperties("too-short", Duration.ofDays(7)));

        assertThatThrownBy(service::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }
}
