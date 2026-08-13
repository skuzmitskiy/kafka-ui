package com.provectus.kafka.ui.config.auth;

import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * Default application role for LDAP users who are not in a configured group.
 */
public enum LdapDefaultRole {
  READ,
  READ_WRITE,
  NONE;

  public static LdapDefaultRole from(String value) {
    if (!StringUtils.hasText(value)) {
      return READ;
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    try {
      return LdapDefaultRole.valueOf(normalized);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(
          "auth.ldap.default-role must be READ, READ_WRITE, or NONE, got: " + value, ex);
    }
  }
}
