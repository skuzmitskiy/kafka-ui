package com.provectus.kafka.ui.controller;

import com.provectus.kafka.ui.config.auth.LdapGroupRoleMapper;
import com.provectus.kafka.ui.config.auth.LocalUserRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Current-user endpoint for {@code AUTH_TYPE=LDAP}. Local user management stays on
 * {@link LocalUsersController} ({@code LOGIN_FORM} only).
 */
@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(value = "auth.type", havingValue = "LDAP")
public class LdapAuthController {

  @GetMapping("/me")
  public Mono<AuthUserView> currentUser(Authentication authentication) {
    return Mono.fromSupplier(() -> {
      LocalUserRole role = LdapGroupRoleMapper.resolveRole(authentication)
          .orElseThrow(() -> new ResponseStatusException(
              HttpStatus.FORBIDDEN, "No application role mapped for LDAP user"));
      return new AuthUserView(authentication.getName(), role, false);
    });
  }

  @Data
  @AllArgsConstructor
  public static class AuthUserView {
    private String username;
    private LocalUserRole role;
    private boolean canManageUsers;
  }
}
