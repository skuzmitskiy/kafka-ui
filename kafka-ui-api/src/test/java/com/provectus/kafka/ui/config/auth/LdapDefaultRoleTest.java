package com.provectus.kafka.ui.config.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LdapDefaultRoleTest {

  @Test
  void parsesKnownValues() {
    assertThat(LdapDefaultRole.from("read")).isEqualTo(LdapDefaultRole.READ);
    assertThat(LdapDefaultRole.from("READ_WRITE")).isEqualTo(LdapDefaultRole.READ_WRITE);
    assertThat(LdapDefaultRole.from("none")).isEqualTo(LdapDefaultRole.NONE);
    assertThat(LdapDefaultRole.from(null)).isEqualTo(LdapDefaultRole.READ);
  }

  @Test
  void rejectsUnknownValues() {
    assertThatThrownBy(() -> LdapDefaultRole.from("ADMIN"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("default-role");
  }
}
