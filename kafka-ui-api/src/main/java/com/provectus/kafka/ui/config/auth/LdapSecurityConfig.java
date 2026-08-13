package com.provectus.kafka.ui.config.auth;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.ldap.LdapAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.ldap.core.support.BaseLdapPathContextSource;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManagerAdapter;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.ldap.authentication.AbstractLdapAuthenticationProvider;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.authentication.NullLdapAuthoritiesPopulator;
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.ldap.search.LdapUserSearch;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.util.StringUtils;

@Configuration
@EnableWebFluxSecurity
@ConditionalOnProperty(value = "auth.type", havingValue = "LDAP")
@Import(LdapAutoConfiguration.class)
@EnableConfigurationProperties(LdapProperties.class)
@Slf4j
public class LdapSecurityConfig extends AbstractAuthSecurityConfig {

  @Value("${spring.ldap.urls}")
  private String ldapUrls;
  @Value("${spring.ldap.base:}")
  private String ldapBase;
  @Value("${spring.ldap.dn.pattern:#{null}}")
  private String ldapUserDnPattern;
  @Value("${spring.ldap.adminUser:#{null}}")
  private String adminUser;
  @Value("${spring.ldap.adminPassword:#{null}}")
  private String adminPassword;
  @Value("${spring.ldap.userFilter.searchBase:#{null}}")
  private String userFilterSearchBase;
  @Value("${spring.ldap.userFilter.searchFilter:#{null}}")
  private String userFilterSearchFilter;

  /**
   * Legacy ASCII AD flag (pre-{@code auth.ldap.active-directory.enabled}).
   */
  @Value("${oauth2.ldap.activeDirectory:#{null}}")
  private Boolean legacyActiveDirectory;

  /**
   * Legacy ASCII AD domain.
   */
  @Value("${oauth2.ldap.activeDirectory.domain:#{null}}")
  private String legacyActiveDirectoryDomain;

  /**
   * Historical typo: Cyrillic {@code с} in {@code aсtiveDirectory}. Kept so existing deployments
   * continue to resolve the domain property.
   */
  @Value("${oauth2.ldap.aсtiveDirectory.domain:#{null}}")
  private String legacyActiveDirectoryDomainCyrillic;

  private final LdapProperties ldapProperties;

  public LdapSecurityConfig(LdapProperties ldapProperties) {
    this.ldapProperties = ldapProperties;
  }

  @Bean
  public LdapGroupRoleMapper ldapGroupRoleMapper() {
    return new LdapGroupRoleMapper(ldapProperties);
  }

  @Bean
  public ReactiveAuthenticationManager authenticationManager(
      BaseLdapPathContextSource contextSource, LdapGroupRoleMapper roleMapper) {
    AbstractLdapAuthenticationProvider authenticationProvider;
    if (isActiveDirectory()) {
      String domain = resolveActiveDirectoryDomain();
      if (!StringUtils.hasText(domain)) {
        throw new IllegalStateException(
            "Active Directory LDAP requires auth.ldap.active-directory.domain "
                + "(or legacy oauth2.ldap.activeDirectory.domain)");
      }
      ActiveDirectoryLdapAuthenticationProvider adProvider =
          new ActiveDirectoryLdapAuthenticationProvider(domain, ldapUrls);
      adProvider.setUseAuthenticationRequestCredentials(true);
      adProvider.setAuthoritiesMapper(roleMapper);
      authenticationProvider = adProvider;
    } else {
      BindAuthenticator ba = new BindAuthenticator(contextSource);
      if (ldapUserDnPattern != null) {
        ba.setUserDnPatterns(new String[] {ldapUserDnPattern});
      }
      if (userFilterSearchFilter != null) {
        LdapUserSearch userSearch =
            new FilterBasedLdapUserSearch(userFilterSearchBase, userFilterSearchFilter, contextSource);
        ba.setUserSearch(userSearch);
      }
      LdapAuthenticationProvider ldapProvider =
          new LdapAuthenticationProvider(ba, authoritiesPopulator(contextSource));
      ldapProvider.setAuthoritiesMapper(roleMapper);
      authenticationProvider = ldapProvider;
    }

    AuthenticationManager am = new ProviderManager(List.of(authenticationProvider));
    return new ReactiveAuthenticationManagerAdapter(am);
  }

  @Bean
  public BaseLdapPathContextSource contextSource() {
    LdapContextSource ctx = new LdapContextSource();
    ctx.setUrl(ldapUrls);
    ctx.setBase(ldapBase);
    ctx.setUserDn(adminUser);
    ctx.setPassword(adminPassword);
    ctx.afterPropertiesSet();
    return ctx;
  }

  @Bean
  public SecurityWebFilterChain configureLdap(ServerHttpSecurity http) {
    log.info("Configuring LDAP authentication with role mapping.");
    if (isActiveDirectory()) {
      log.info("Active Directory support for LDAP has been enabled.");
    }
    log.info(
        "LDAP role mapping: readGroup={}, readWriteGroup={}, defaultRole={}",
        ldapProperties.getReadGroup(),
        ldapProperties.getReadWriteGroup(),
        ldapProperties.resolvedDefaultRole());

    return http
        .csrf().disable()
        .authorizeExchange()
        .pathMatchers(AUTH_WHITELIST).permitAll()
        .pathMatchers("/api/auth/users", "/api/auth/users/**").denyAll()
        .pathMatchers(HttpMethod.GET, "/api/info").hasAnyRole(
            LocalUserRole.READ.name(), LocalUserRole.READ_WRITE.name())
        .pathMatchers("/api/config", "/api/config/**")
        .hasRole(LocalUserRole.READ_WRITE.name())
        .pathMatchers(HttpMethod.GET, "/**").hasAnyRole(
            LocalUserRole.READ.name(), LocalUserRole.READ_WRITE.name())
        .pathMatchers(HttpMethod.HEAD, "/**").hasAnyRole(
            LocalUserRole.READ.name(), LocalUserRole.READ_WRITE.name())
        .pathMatchers(HttpMethod.OPTIONS, "/**").hasAnyRole(
            LocalUserRole.READ.name(), LocalUserRole.READ_WRITE.name())
        .anyExchange().hasRole(LocalUserRole.READ_WRITE.name())
        .and()
        .httpBasic()
        .and()
        .build();
  }

  private LdapAuthoritiesPopulator authoritiesPopulator(BaseLdapPathContextSource contextSource) {
    if (!StringUtils.hasText(ldapProperties.getGroupSearchBase())) {
      return new NullLdapAuthoritiesPopulator();
    }
    DefaultLdapAuthoritiesPopulator populator =
        new DefaultLdapAuthoritiesPopulator(contextSource, ldapProperties.getGroupSearchBase());
    populator.setGroupSearchFilter(ldapProperties.getGroupSearchFilter());
    populator.setGroupRoleAttribute(ldapProperties.getGroupRoleAttribute());
    populator.setSearchSubtree(ldapProperties.isGroupSearchSubtree());
    return populator;
  }

  private boolean isActiveDirectory() {
    if (ldapProperties.getActiveDirectory() != null
        && ldapProperties.getActiveDirectory().isEnabled()) {
      return true;
    }
    return Boolean.TRUE.equals(legacyActiveDirectory);
  }

  private String resolveActiveDirectoryDomain() {
    if (ldapProperties.getActiveDirectory() != null
        && StringUtils.hasText(ldapProperties.getActiveDirectory().getDomain())) {
      return ldapProperties.getActiveDirectory().getDomain();
    }
    if (StringUtils.hasText(legacyActiveDirectoryDomain)) {
      return legacyActiveDirectoryDomain;
    }
    return legacyActiveDirectoryDomainCyrillic;
  }

}
