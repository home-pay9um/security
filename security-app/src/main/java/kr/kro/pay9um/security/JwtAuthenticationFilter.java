package kr.kro.pay9um.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.kro.pay9um.core.common.exception.RefreshTokenStoreUnavailableException;
import kr.kro.pay9um.core.security.CustomUserDetailsService;
import kr.kro.pay9um.jwt.JwtPayload;
import kr.kro.pay9um.jwt.TokenType;
import kr.kro.pay9um.jwt.validator.JwtTokenValidator;
import kr.kro.pay9um.service.token.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenValidator jwtTokenValidator;
    private final CustomUserDetailsService customUserDetailsService;
    private final RefreshTokenStore refreshTokenStore;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveBearerToken(request);

        if (StringUtils.hasText(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            JwtPayload payload = jwtTokenValidator.validate(token, TokenType.ACCESS).orElse(null);
            if (payload != null) {
                try {
                    if (!refreshTokenStore.isSessionActive(payload.sessionId())) {
                        log.debug("JWT session is no longer active: {}", payload.sessionId());
                    } else {
                        UserDetails userDetails = customUserDetailsService.loadUserByUid(payload.uid());
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (UsernameNotFoundException e) {
                    SecurityContextHolder.clearContext();
                    log.debug("JWT subject no longer exists: {}", payload.uid());
                } catch (RefreshTokenStoreUnavailableException e) {
                    SecurityContextHolder.clearContext();
                    writeServiceUnavailable(response);
                    return;
                } catch (RuntimeException e) {
                    SecurityContextHolder.clearContext();
                    log.warn("Could not set authentication from JWT", e);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }

    private void writeServiceUnavailable(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "message", "인증 서비스에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해 주세요."
        ));
    }
}
