package com.library.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminRoleFilterTest {

    private AdminRoleFilter adminRoleFilter;

    @BeforeEach
    void setup() {
        adminRoleFilter = new AdminRoleFilter();
    }

    private GatewayFilter filter() {
        return adminRoleFilter.apply(new AdminRoleFilter.Config());
    }

    // ── ADMIN passes through ──────────────────────────────────────────────────

    @Test
    void adminRole_chainContinues() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/admin/users")
                .header("X-User-Role", "ADMIN")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    // ── Non-admin roles return 403 ────────────────────────────────────────────

    @Test
    void studentRole_returns403() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/admin/dashboard")
                .header("X-User-Role", "STUDENT")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(any());
    }

    @Test
    void noRoleHeader_returns403() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/admin/seats").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(any());
    }

    @Test
    void emptyRoleHeader_returns403() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/admin/users")
                .header("X-User-Role", "")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(any());
    }

    @Test
    void lowercaseAdmin_returns403() {
        // Comparison is case-sensitive: "admin" != "ADMIN"
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/admin/users")
                .header("X-User-Role", "admin")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(any());
    }

    @Test
    void adminWithTrailingSpace_returns403() {
        // "ADMIN " (with space) is not exactly "ADMIN"
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/admin/users")
                .header("X-User-Role", "ADMIN ")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(any());
    }

    @Test
    void arbitraryRole_returns403() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/admin/reports")
                .header("X-User-Role", "SUPERUSER")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(any());
    }

    // ── Config inner class ────────────────────────────────────────────────────

    @Test
    void config_canBeInstantiated() {
        AdminRoleFilter.Config config = new AdminRoleFilter.Config();
        assertThat(config).isNotNull();
    }
}
