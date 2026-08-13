package com.provectus.kafka.ui.config.auth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.util.StringUtils;

/**
 * Maps LDAP / AD group authorities onto {@code ROLE_READ} / {@code ROLE_READ_WRITE}.
 */
public class LdapGroupRoleMapper implements GrantedAuthoritiesMapper {

  private static final Pattern CN_PATTERN =
      Pattern.compile("(?i)(?:^|,)\\s*cn\\s*=\\s*([^,]+)");

  private final Set<String> readGroupAliases;
  private final Set<String> readWriteGroupAliases;
  private final LdapDefaultRole defaultRole;

  public LdapGroupRoleMapper(LdapProperties properties) {
    this(
        properties.getReadGroup(),
        properties.getReadWriteGroup(),
        properties.resolvedDefaultRole());
  }

  public LdapGroupRoleMapper(String readGroup, String readWriteGroup, LdapDefaultRole defaultRole) {
    this.readGroupAliases = configuredAliases(readGroup);
    this.readWriteGroupAliases = configuredAliases(readWriteGroup);
    this.defaultRole = defaultRole == null ? LdapDefaultRole.READ : defaultRole;
  }

  @Override
  public Collection<? extends GrantedAuthority> mapAuthorities(
      Collection<? extends GrantedAuthority> authorities) {
    boolean matchedRead = false;
    boolean matchedReadWrite = false;

    for (GrantedAuthority authority : authorities) {
      for (String candidate : expandAuthorityNames(authority.getAuthority())) {
        if (readWriteGroupAliases.contains(candidate)) {
          matchedReadWrite = true;
        }
        if (readGroupAliases.contains(candidate)) {
          matchedRead = true;
        }
      }
    }

    Set<GrantedAuthority> mapped = new LinkedHashSet<>();
    if (matchedReadWrite) {
      mapped.add(role(LocalUserRole.READ_WRITE));
    } else if (matchedRead) {
      mapped.add(role(LocalUserRole.READ));
    } else if (defaultRole == LdapDefaultRole.READ) {
      mapped.add(role(LocalUserRole.READ));
    } else if (defaultRole == LdapDefaultRole.READ_WRITE) {
      mapped.add(role(LocalUserRole.READ_WRITE));
    }
    return Collections.unmodifiableSet(mapped);
  }

  /**
   * Derives the application role from Spring Security authorities (after mapping).
   */
  public static Optional<LocalUserRole> resolveRole(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return Optional.empty();
    }
    return resolveRole(authentication.getAuthorities());
  }

  public static Optional<LocalUserRole> resolveRole(
      Collection<? extends GrantedAuthority> authorities) {
    if (authorities == null || authorities.isEmpty()) {
      return Optional.empty();
    }
    boolean readWrite = false;
    boolean read = false;
    for (GrantedAuthority authority : authorities) {
      String name = authority.getAuthority();
      if ((LocalUserStore.ROLE_PREFIX + LocalUserRole.READ_WRITE.name()).equals(name)) {
        readWrite = true;
      } else if ((LocalUserStore.ROLE_PREFIX + LocalUserRole.READ.name()).equals(name)) {
        read = true;
      }
    }
    if (readWrite) {
      return Optional.of(LocalUserRole.READ_WRITE);
    }
    if (read) {
      return Optional.of(LocalUserRole.READ);
    }
    return Optional.empty();
  }

  static Collection<String> expandAuthorityNames(String raw) {
    if (!StringUtils.hasText(raw)) {
      return Collections.emptyList();
    }
    Collection<String> names = new ArrayList<>();
    String value = raw.trim();
    names.add(normalizeAuthorityToken(value));

    String withoutRole = stripRolePrefix(value);
    if (!withoutRole.equalsIgnoreCase(value)) {
      names.add(normalizeAuthorityToken(withoutRole));
    }

    Matcher matcher = CN_PATTERN.matcher(value);
    while (matcher.find()) {
      names.add(normalizeAuthorityToken(matcher.group(1)));
    }
    return names;
  }

  private static Set<String> configuredAliases(String value) {
    if (!StringUtils.hasText(value)) {
      return Collections.emptySet();
    }
    return Set.copyOf(expandAuthorityNames(value.trim()));
  }

  private static String stripRolePrefix(String value) {
    if (value.regionMatches(true, 0, LocalUserStore.ROLE_PREFIX, 0, LocalUserStore.ROLE_PREFIX.length())) {
      return value.substring(LocalUserStore.ROLE_PREFIX.length());
    }
    return value;
  }

  private static String normalizeAuthorityToken(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private static GrantedAuthority role(LocalUserRole role) {
    return new SimpleGrantedAuthority(LocalUserStore.ROLE_PREFIX + role.name());
  }
}
