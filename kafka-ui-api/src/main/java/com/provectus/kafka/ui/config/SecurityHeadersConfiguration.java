package com.provectus.kafka.ui.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Configuration
public class SecurityHeadersConfiguration {

  @Bean
  public WebFilter securityHeadersFilter() {
    return (final ServerWebExchange ctx, final WebFilterChain chain) -> {
      final HttpHeaders headers = ctx.getResponse().getHeaders();
      headers.add("X-Content-Type-Options", "nosniff");
      headers.add("X-Frame-Options", "DENY");
      headers.add("X-XSS-Protection", "1; mode=block");
      headers.add("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
      headers.add("Referrer-Policy", "strict-origin-when-cross-origin");
      headers.add("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
      headers.add("Content-Security-Policy",
          "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; "
              + "img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'");
      return chain.filter(ctx);
    };
  }

}
