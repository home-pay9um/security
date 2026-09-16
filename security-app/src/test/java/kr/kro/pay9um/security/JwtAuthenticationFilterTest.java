package kr.kro.pay9um.security;

import kr.kro.pay9um.core.domain.user.entity.User;
import kr.kro.pay9um.core.security.CustomUserDetails;
import kr.kro.pay9um.core.security.CustomUserDetailsService;
import kr.kro.pay9um.jwt.config.JwtProperties;
import kr.kro.pay9um.jwt.provider.JwtTokenProvider;
import kr.kro.pay9um.jwt.validator.JwtTokenValidator;
import kr.kro.pay9um.service.token.RefreshTokenStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC);
    private static final JwtProperties JWT_PROPERTIES = new JwtProperties(
            "test-secret-key-must-be-at-least-32-characters-long",
            21_600_000,
            1_209_600_000
    );

    @Mock
    private CustomUserDetailsService customUserDetailsService;
    @Mock
    private RefreshTokenStore refreshTokenStore;
    @Mock
    private ObjectMapper objectMapper;

    private JwtTokenProvider jwtTokenProvider;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(JWT_PROPERTIES, CLOCK);
        filter = new JwtAuthenticationFilter(
                new JwtTokenValidator(JWT_PROPERTIES, CLOCK),
                customUserDetailsService,
                refreshTokenStore,
                objectMapper
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesOnlyWhenTheJwtSessionIsActive() throws Exception {
        String accessToken = jwtTokenProvider.generateAccessToken("user-uid", "session-id");
        when(refreshTokenStore.isSessionActive("session-id")).thenReturn(true);
        when(customUserDetailsService.loadUserByUid("user-uid")).thenReturn(new CustomUserDetails(testUser()));

        doFilter(accessToken);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(customUserDetailsService).loadUserByUid("user-uid");
    }

    @Test
    void rejectsAccessTokenAfterItsSessionIsRevoked() throws Exception {
        String accessToken = jwtTokenProvider.generateAccessToken("user-uid", "session-id");
        when(refreshTokenStore.isSessionActive("session-id")).thenReturn(false);

        doFilter(accessToken);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(customUserDetailsService, never()).loadUserByUid(anyString());
    }

    private void doFilter(String accessToken) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + accessToken);
        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> {
        });
    }

    private User testUser() {
        return User.builder()
                .uid("user-uid")
                .username("user1234")
                .password("encoded-password")
                .email("user1234@example.com")
                .nickname("사용자")
                .phoneNumber("010-1234-5678")
                .build();
    }
}
