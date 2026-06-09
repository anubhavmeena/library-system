package com.library.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthFilterTest {

    private static final String JWT_SECRET = "test-jwt-secret-key-for-unit-testing-purposes-only!";

    private AuthFilter authFilter;

    @BeforeEach
    void setup() {
        authFilter = new AuthFilter();
        ReflectionTestUtils.setField(authFilter, "jwtSecret", JWT_SECRET);
    }

    private GatewayFilter filter() {
        return authFilter.apply(new AuthFilter.Config());
    }

    private String buildToken(String userId, String role, boolean expired) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        Instant expiry = expired
                ? Instant.now().minus(Duration.ofHours(1))
                : Instant.now().plus(Duration.ofHours(1));
        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    // ── Public path bypass ────────────────────────────────────────────────────

    @Test
    void publicPath_sendOtp_bypassesAuth() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/send-otp").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void publicPath_verifyOtp_bypassesAuth() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/verify-otp").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void publicPath_register_bypassesAuth() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/register").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void publicPath_login_bypassesAuth() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void publicPath_plans_bypassesAuth() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/plans").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void publicPath_sendOtpSubpath_bypassesAuth() {
        // path.startsWith("/api/auth/send-otp") is true for longer sub-paths
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/send-otp/resend").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    void publicPath_plansSubpath_bypassesAuth() {
        // /api/plans/123 starts with /api/plans → bypassed
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/plans/123").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    void authAdminLogin_notInPublicPaths_returns401WhenNoToken() {
        // /api/auth/admin/login does NOT start with /api/auth/login
        // (the filter never fires here in practice because the auth route has no AuthFilter,
        //  but if it did, no-token should be rejected)
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/admin/login").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        // /api/auth/admin/login starts with /api/auth/login? No → not public
        // But wait: it does start with... actually, does "/api/auth/admin/login"
        // startsWith("/api/auth/login")? The 10th char in the path is 'a' (from "admin"),
        // but "/api/auth/login" has 'l' at position 10 → NO match. Returns 401.
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── 401 on missing / invalid Authorization header ─────────────────────────

    @Test
    void protectedPath_noAuthHeader_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users/me").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void protectedPath_basicAuthScheme_returns401() {
        // "Basic ..." doesn't start with "Bearer " → rejected
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void protectedPath_bearerKeywordOnly_returns401() {
        // "Bearer" without space and token → doesn't start with "Bearer "
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void protectedPath_bearerWithEmptyToken_returns401() {
        // "Bearer " followed by nothing → JJWT parse fails, caught by catch block
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/seats/availability")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void protectedPath_malformedToken_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid.jwt")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void protectedPath_tokenSignedWithWrongKey_returns401() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "wrong-secret-totally-different-key-32-bytes!!".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("user-123")
                .claim("role", "STUDENT")
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .signWith(wrongKey)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void protectedPath_expiredToken_returns401() {
        String token = buildToken(UUID.randomUUID().toString(), "STUDENT", true);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/memberships/my")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    // ── Valid token — header injection ────────────────────────────────────────

    @Test
    void validToken_chainCalledWithModifiedExchange() {
        String userId = UUID.randomUUID().toString();
        String token = buildToken(userId, "STUDENT", false);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any());
        // Error status not set on the original exchange
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void validToken_xUserIdHeaderInjected() {
        String userId = UUID.randomUUID().toString();
        String token = buildToken(userId, "STUDENT", false);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        assertThat(captor.getValue().getRequest().getHeaders().getFirst("X-User-Id"))
                .isEqualTo(userId);
    }

    @Test
    void validToken_xUserRoleHeaderInjected() {
        String userId = UUID.randomUUID().toString();
        String token = buildToken(userId, "STUDENT", false);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        assertThat(captor.getValue().getRequest().getHeaders().getFirst("X-User-Role"))
                .isEqualTo("STUDENT");
    }

    @Test
    void validAdminToken_adminRoleAndIdBothInjected() {
        String userId = UUID.randomUUID().toString();
        String token = buildToken(userId, "ADMIN", false);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        ServerWebExchange captured = captor.getValue();
        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo(userId);
        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("ADMIN");
    }

    @Test
    void validToken_noRoleClaim_nullRoleHeader() {
        // Token with no "role" claim → X-User-Role header set to null
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("user-without-role")
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .signWith(key)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        assertThat(captor.getValue().getRequest().getHeaders().getFirst("X-User-Role")).isNull();
    }

    @Test
    void validToken_seatServicePath_passes() {
        String userId = UUID.randomUUID().toString();
        String token = buildToken(userId, "STUDENT", false);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/seats/availability")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    void validToken_membershipPath_passes() {
        String userId = UUID.randomUUID().toString();
        String token = buildToken(userId, "STUDENT", false);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/memberships/my")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any());
    }

    // ── Config inner class ────────────────────────────────────────────────────

    @Test
    void config_canBeInstantiated() {
        AuthFilter.Config config = new AuthFilter.Config();
        assertThat(config).isNotNull();
    }
}
