package com.provectus.kafka.ui.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.provectus.kafka.ui.config.auth.LocalUserRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class LdapAuthControllerTest {

  private final LdapAuthController controller = new LdapAuthController();

  @Test
  void returnsUsernameAndMappedRole() {
    var authentication = new UsernamePasswordAuthenticationToken(
        "ldap-user",
        "n/a",
        List.of(new SimpleGrantedAuthority("ROLE_READ_WRITE")));

    LdapAuthController.AuthUserView view = controller.currentUser(authentication).block();

    assertThat(view).isNotNull();
    assertThat(view.getUsername()).isEqualTo("ldap-user");
    assertThat(view.getRole()).isEqualTo(LocalUserRole.READ_WRITE);
    assertThat(view.isCanManageUsers()).isFalse();
  }

  @Test
  void forbidsWhenNoApplicationRole() {
    var authentication = new UsernamePasswordAuthenticationToken(
        "ldap-user", "n/a", List.of());

    assertThatThrownBy(() -> controller.currentUser(authentication).block())
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatus())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }
}
