package com.provectus.kafka.ui.config.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(value = "auth.type", havingValue = "LOGIN_FORM")
public class LocalUserStore implements ReactiveUserDetailsService {

  public static final String ROLE_PREFIX = "ROLE_";

  private final ObjectMapper objectMapper;
  private final PasswordEncoder passwordEncoder;
  private final Path usersFile;
  private UsersDocument document;

  public LocalUserStore(
      ObjectMapper objectMapper,
      PasswordEncoder passwordEncoder,
      @Value("${auth.local.users-file:/etc/kafkaui/users.json}") String usersFile,
      @Value("${auth.local.bootstrap.username:${spring.security.user.name:admin}}")
          String bootstrapUsername,
      @Value("${auth.local.bootstrap.password:${spring.security.user.password:}}")
          String bootstrapPassword) {
    this.objectMapper = objectMapper;
    this.passwordEncoder = passwordEncoder;
    this.usersFile = Path.of(usersFile);
    this.document = loadOrCreate(bootstrapUsername, bootstrapPassword);
  }

  @Override
  public Mono<UserDetails> findByUsername(String username) {
    return Mono.fromSupplier(() -> find(username)
            .<UserDetails>map(account -> User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .authorities(ROLE_PREFIX + account.getRole().name())
                .build())
            .orElseThrow(() -> new UsernameNotFoundException(username)));
  }

  public synchronized List<LocalUserView> list() {
    return document.getUsers().stream()
        .map(LocalUserView::from)
        .sorted(Comparator.comparing(LocalUserView::getUsername))
        .toList();
  }

  public synchronized LocalUserView create(String username, String password, LocalUserRole role) {
    validateUsername(username);
    validatePassword(password);
    if (find(username).isPresent()) {
      throw new IllegalArgumentException("User already exists");
    }
    var account = new StoredUser(username, passwordEncoder.encode(password), requireRole(role));
    document.getUsers().add(account);
    save();
    return LocalUserView.from(account);
  }

  public synchronized LocalUserView update(
      String username, String password, LocalUserRole role) {
    var account = find(username)
        .orElseThrow(() -> new UsernameNotFoundException(username));
    var newRole = requireRole(role);
    if (account.getRole() == LocalUserRole.READ_WRITE
        && newRole != LocalUserRole.READ_WRITE
        && countReadWriteUsers() == 1) {
      throw new IllegalArgumentException("At least one read/write user is required");
    }
    if (StringUtils.hasText(password)) {
      validatePassword(password);
      account.setPasswordHash(passwordEncoder.encode(password));
    }
    account.setRole(newRole);
    save();
    return LocalUserView.from(account);
  }

  public synchronized void delete(String username, String currentUsername) {
    if (username.equalsIgnoreCase(currentUsername)) {
      throw new IllegalArgumentException("You cannot delete your own account");
    }
    var account = find(username)
        .orElseThrow(() -> new UsernameNotFoundException(username));
    if (account.getRole() == LocalUserRole.READ_WRITE && countReadWriteUsers() == 1) {
      throw new IllegalArgumentException("At least one read/write user is required");
    }
    document.getUsers().remove(account);
    save();
  }

  public synchronized LocalUserView get(String username) {
    return find(username).map(LocalUserView::from)
        .orElseThrow(() -> new UsernameNotFoundException(username));
  }

  private Optional<StoredUser> find(String username) {
    return document.getUsers().stream()
        .filter(user -> user.getUsername().equalsIgnoreCase(username))
        .findFirst();
  }

  private long countReadWriteUsers() {
    return document.getUsers().stream()
        .filter(user -> user.getRole() == LocalUserRole.READ_WRITE)
        .count();
  }

  private UsersDocument loadOrCreate(String bootstrapUsername, String bootstrapPassword) {
    try {
      if (Files.exists(usersFile)) {
        var loaded = objectMapper.readValue(usersFile.toFile(), UsersDocument.class);
        if (loaded.getUsers() == null || loaded.getUsers().isEmpty()) {
          throw new IllegalStateException("Users file must contain at least one account");
        }
        return loaded;
      }
      validateUsername(bootstrapUsername);
      validatePassword(bootstrapPassword);
      var created = new UsersDocument(new ArrayList<>(List.of(
          new StoredUser(
              bootstrapUsername,
              passwordEncoder.encode(bootstrapPassword),
              LocalUserRole.READ_WRITE))));
      document = created;
      save();
      return created;
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read users file " + usersFile, e);
    }
  }

  private void save() {
    try {
      var parent = usersFile.toAbsolutePath().getParent();
      Files.createDirectories(parent);
      var temporaryFile = Files.createTempFile(parent, "users-", ".json");
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporaryFile.toFile(), document);
      try {
        Files.setPosixFilePermissions(temporaryFile, EnumSet.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
      } catch (UnsupportedOperationException ignored) {
        // Non-POSIX filesystems still get an atomic replacement below.
      }
      try {
        Files.move(temporaryFile, usersFile, StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException atomicMoveFailure) {
        Files.move(temporaryFile, usersFile, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Cannot save users file " + usersFile, e);
    }
  }

  private static void validateUsername(String username) {
    if (!StringUtils.hasText(username)
        || !username.matches("[A-Za-z0-9._@-]{3,64}")) {
      throw new IllegalArgumentException(
          "Username must be 3-64 characters and contain only letters, digits, . _ @ or -");
    }
  }

  private static void validatePassword(String password) {
    if (!StringUtils.hasText(password) || password.length() < 8) {
      throw new IllegalArgumentException("Password must contain at least 8 characters");
    }
  }

  private static LocalUserRole requireRole(LocalUserRole role) {
    if (role == null) {
      throw new IllegalArgumentException("Role is required");
    }
    return role;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  static class UsersDocument {
    private List<StoredUser> users = new ArrayList<>();
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  static class StoredUser {
    private String username;
    private String passwordHash;
    private LocalUserRole role;
  }

  @Data
  @AllArgsConstructor
  public static class LocalUserView {
    private String username;
    private LocalUserRole role;

    static LocalUserView from(StoredUser account) {
      return new LocalUserView(account.getUsername(), account.getRole());
    }
  }
}
