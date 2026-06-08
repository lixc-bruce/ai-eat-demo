package com.eat.interceptor;

import com.eat.common.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;

    public static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    private static final String[] PUBLIC_PATHS = {
            "/api/v1/user/send-code",
            "/api/v1/user/login",
            "/api/v1/meal/fallback",
            "/api/v1/qa/hot-questions"
    };

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        String path = request.getRequestURI();

        for (String publicPath : PUBLIC_PATHS) {
            if (path.equals(publicPath)) return true;
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        String token = header.substring(7);
        try {
            Long userId = jwtUtil.parseUserId(token);

            String cachedToken = (String) redisTemplate.opsForValue()
                    .get("token:user:" + userId);
            if (cachedToken == null) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                return false;
            }

            USER_ID_HOLDER.set(userId);
            return true;
        } catch (JwtUtil.JwtExpiredException e) {
            log.debug("JWT expired: {}", e.getMessage());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        } catch (JwtUtil.JwtInvalidException e) {
            log.debug("JWT invalid: {}", e.getMessage());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        USER_ID_HOLDER.remove();
    }
}
