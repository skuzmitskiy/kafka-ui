package com.provectus.kafka.ui.config.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class LdapGroupRoleMapperTest {

  @Test
  void mapsReadWriteGroupPreferringWriteOverRead() {
    var mapper = new LdapGroupRoleMapper("kafka-ui-readers", "kafka-ui-admins", LdapDefaultRole.NONE);

    Set<String> roles = authorityNames(mapper.mapAuthorities(List.of(
        new SimpleGrantedAuthority("ROLE_KAFKA-UI-READERS"),
        new SimpleGrantedAuthority("ROLE_KAFKA-UI-ADMINS"))));

    assertThat(roles).containsExactly("ROLE_READ_WRITE");
  }

  @Test
  void mapsReadGroupCaseInsensitively() {
    var mapper = new LdapGroupRoleMapper("Kafka-UI-Readers", null, LdapDefaultRole.NONE);

    Set<String> roles = authorityNames(mapper.mapAuthorities(List.of(
        new SimpleGrantedAuthority("role_kafka-ui-readers"))));

    assertThat(roles).containsExactly("ROLE_READ");
  }

  @Test
  void mapsDnFormAndConfiguredRolePrefix() {
    var mapper = new LdapGroupRoleMapper(
        "ROLE_readers", "CN=Writers,OU=Groups,DC=example,DC=com", LdapDefaultRole.NONE);

    assertThat(authorityNames(mapper.mapAuthorities(List.of(
        new SimpleGrantedAuthority("CN=Readers,OU=Groups,DC=example,DC=com")))))
        .containsExactly("ROLE_READ");

    assertThat(authorityNames(mapper.mapAuthorities(List.of(
        new SimpleGrantedAuthority("ROLE_WRITERS")))))
        .containsExactly("ROLE_READ_WRITE");
  }

  @Test
  void appliesDefaultReadWhenNoGroupMatches() {
    var mapper = new LdapGroupRoleMapper("readers", "admins", LdapDefaultRole.READ);

    assertThat(authorityNames(mapper.mapAuthorities(List.of(
        new SimpleGrantedAuthority("ROLE_OTHER")))))
        .containsExactly("ROLE_READ");
  }

  @Test
  void defaultNoneGrantsNoRolesWhenUnmatched() {
    var mapper = new LdapGroupRoleMapper("readers", "admins", LdapDefaultRole.NONE);

    assertThat(mapper.mapAuthorities(List.of(
        new SimpleGrantedAuthority("ROLE_OTHER")))).isEmpty();
  }

  @Test
  void resolveRolePrefersReadWrite() {
    var auth = new UsernamePasswordAuthenticationToken(
        "alice",
        "n/a",
        List.of(
            new SimpleGrantedAuthority("ROLE_READ"),
            new SimpleGrantedAuthority("ROLE_READ_WRITE")));

    assertThat(LdapGroupRoleMapper.resolveRole(auth)).contains(LocalUserRole.READ_WRITE);
  }

  @Test
  void resolveRoleEmptyWithoutAppRoles() {
    var auth = new UsernamePasswordAuthenticationToken(
        "alice", "n/a", List.of(new SimpleGrantedAuthority("ROLE_OTHER")));

    assertThat(LdapGroupRoleMapper.resolveRole(auth)).isEmpty();
  }

  private static Set<String> authorityNames(Iterable<? extends GrantedAuthority> authorities) {
    return ((java.util.Collection<? extends GrantedAuthority>) authorities).stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
  }
}
