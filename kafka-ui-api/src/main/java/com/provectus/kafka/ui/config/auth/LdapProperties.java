package com.provectus.kafka.ui.config.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LDAP role / group mapping settings ({@code auth.ldap.*} / {@code AUTH_LDAP_*}).
 *
 * <p>Connection settings stay under {@code spring.ldap.*}. Active Directory can also be enabled
 * via legacy {@code oauth2.ldap.activeDirectory*} keys (including the historical Cyrillic typo).
 */
@Data
@ConfigurationProperties(prefix = "auth.ldap")
public class LdapProperties {

  /**
   * LDAP group name (CN or simple name) mapped to {@link LocalUserRole#READ}.
   * Compared case-insensitively; {@code ROLE_} prefix and DN {@code CN=} forms are accepted.
   */
  private String readGroup;

  /**
   * LDAP group name mapped to {@link LocalUserRole#READ_WRITE}.
   */
  private String readWriteGroup;

  /**
   * Role granted when the user is not in a configured mapped group.
   * {@code READ} (default) preserves pre-role LDAP behaviour; {@code NONE} requires an explicit
   * group match; {@code READ_WRITE} is allowed but uncommon.
   */
  private String defaultRole = "READ";

  /**
   * Group search base for non-AD LDAP
   * ({@link org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator}).
   * When empty, group search is skipped and only {@link #defaultRole} applies.
   */
  private String groupSearchBase;

  /**
   * Group search filter. {@code {0}} is the user DN.
   */
  private String groupSearchFilter = "(member={0})";

  /**
   * LDAP attribute used as the group "role" name (typically {@code cn}).
   */
  private String groupRoleAttribute = "cn";

  /**
   * Whether group search is recursive under {@link #groupSearchBase}.
   */
  private boolean groupSearchSubtree = true;

  private ActiveDirectory activeDirectory = new ActiveDirectory();

  @Data
  public static class ActiveDirectory {
    private boolean enabled;
    private String domain;
  }

  public LdapDefaultRole resolvedDefaultRole() {
    return LdapDefaultRole.from(defaultRole);
  }
}
