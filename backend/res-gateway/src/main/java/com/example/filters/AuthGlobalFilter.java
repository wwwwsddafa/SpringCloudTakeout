package com.example.filters;

import com.example.configs.GatewayJwtProvider;
import com.example.web.vo.JwtUserInfo;
import com.example.web.vo.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RefreshScope
@ConfigurationProperties(prefix = "gateway.auth")
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    @Autowired
    private GatewayJwtProvider gatewayJwtProvider;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    private static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";
    private static final String AUTHORIZATION_HEADER = HttpHeaders.AUTHORIZATION;
    private static final String BEARER_PREFIX = "Bearer ";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private List<String> whiteList = new ArrayList<>();

    private Map<String, List<String>> rolePathMapping = new HashMap<>();

    public void setWhiteList(List<String> whiteList) {
        this.whiteList = whiteList;
    }

    public void setRolePathMapping(Map<String, List<String>> rolePathMapping) {
        this.rolePathMapping = rolePathMapping;
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isWhiteListed(path)) {
            log.debug("白名单路径，直接放行: {}", path);
            return chain.filter(exchange);
        }

        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            log.warn("请求缺少Token: {}", path);
            return unauthorizedResponse(exchange, "请先登录");
        }

        return redisTemplate.opsForValue().get(TOKEN_BLACKLIST_PREFIX + token)
                .defaultIfEmpty("")
                .flatMap(blacklisted -> {
                    if (StringUtils.hasText(blacklisted)) {
                        log.warn("Token已失效，在黑名单中");
                        return unauthorizedResponse(exchange, "Token已失效，请重新登录");
                    }

                    if (!gatewayJwtProvider.validateToken(token)) {
                        log.warn("Token校验失败");
                        return unauthorizedResponse(exchange, "Token无效或已过期");
                    }

                    JwtUserInfo userInfo = gatewayJwtProvider.parseToken(token);
                    UserRole role = userInfo.getRole();

                    if (!hasPermission(role, path)) {
                        log.warn("权限不足: role={}, path={}", role, path);
                        return forbiddenResponse(exchange, "权限不足");
                    }

                    ServerHttpRequest mutatedRequest = request.mutate()
                            .header("X-User-Id", userInfo.getUserId())
                            .header("X-Username", userInfo.getUsername())
                            .header("X-User-Role", role.name())
                            .build();

                    log.debug("认证通过: userId={}, role={}, path={}",
                            userInfo.getUserId(), role, path);
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                });
    }

    private boolean isWhiteListed(String path) {
        if (whiteList == null || whiteList.isEmpty()) {
            return false;
        }
        for (String pattern : whiteList) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPermission(UserRole role, String path) {
        if (role == UserRole.ADMIN) {
            return true;
        }

        List<String> paths = rolePathMapping.get(role.name());
        if (paths == null || paths.isEmpty()) {
            return false;
        }

        for (String pattern : paths) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private String extractToken(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":401,\"msg\":\"" + message + "\"}";
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private Mono<Void> forbiddenResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":403,\"msg\":\"" + message + "\"}";
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}