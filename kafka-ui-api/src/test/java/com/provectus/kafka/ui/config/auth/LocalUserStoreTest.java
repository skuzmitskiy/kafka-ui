package com.provectus.kafka.ui.config.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class LocalUserStoreTest {

  @TempDir
  Path temporaryDirectory;

  @Test
  void persistsUsersAndPasswordHashes() {
    var file = temporaryDirectory.resolve("users.json");
    var store = store(file);

    store.create("reader", "reader-password", LocalUserRole.READ);

    var reloaded = store(file);
    assertThat(reloaded.list())
        .extracting(LocalUserStore.LocalUserView::getUsername)
        .containsExactly("admin", "reader");
    assertThat(reloaded.findByUsername("reader").block().getAuthorities())
        .extracting(Object::toString)
        .containsExactly("ROLE_READ");
    assertThat(file).content().doesNotContain("reader-password");
  }

  @Test
  void preventsRemovingTheLastReadWriteUser() {
    var store = store(temporaryDirectory.resolve("users.json"));

    assertThatThrownBy(() -> store.update("admin", null, LocalUserRole.READ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("At least one");
  }

  @Test
  void preventsDeletingCurrentUser() {
    var store = store(temporaryDirectory.resolve("users.json"));
    store.create("writer", "writer-password", LocalUserRole.READ_WRITE);

    assertThatThrownBy(() -> store.delete("writer", "writer"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("own account");
  }

  private LocalUserStore store(Path path) {
    return new LocalUserStore(
        new ObjectMapper(), new BCryptPasswordEncoder(), path.toString(), "admin", "admin-password");
  }
}
