package com.provectus.kafka.ui.controller;

import com.provectus.kafka.ui.config.auth.LocalUserRole;
import com.provectus.kafka.ui.config.auth.LocalUserStore;
import com.provectus.kafka.ui.config.auth.LocalUserStore.LocalUserView;
import java.security.Principal;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(value = "auth.type", havingValue = "LOGIN_FORM")
public class LocalUsersController {

  private final LocalUserStore userStore;

  @GetMapping("/me")
  public Mono<LocalUserView> currentUser(Principal principal) {
    return Mono.fromSupplier(() -> userStore.get(principal.getName()));
  }

  @GetMapping("/users")
  public Mono<List<LocalUserView>> users() {
    return Mono.fromSupplier(userStore::list);
  }

  @PostMapping("/users")
  public Mono<LocalUserView> create(@RequestBody LocalUserRequest request) {
    return execute(() -> userStore.create(
        request.getUsername(), request.getPassword(), request.getRole()));
  }

  @PutMapping("/users/{username}")
  public Mono<LocalUserView> update(
      @PathVariable String username, @RequestBody LocalUserRequest request) {
    return execute(() -> userStore.update(username, request.getPassword(), request.getRole()));
  }

  @DeleteMapping("/users/{username}")
  public Mono<Void> delete(@PathVariable String username, Principal principal) {
    return execute(() -> {
      userStore.delete(username, principal.getName());
      return null;
    });
  }

  private <T> Mono<T> execute(Operation<T> operation) {
    return Mono.fromCallable(operation::execute)
        .onErrorMap(IllegalArgumentException.class,
            e -> new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e))
        .onErrorMap(UsernameNotFoundException.class,
            e -> new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e));
  }

  @FunctionalInterface
  private interface Operation<T> {
    T execute();
  }

  @Data
  public static class LocalUserRequest {
    private String username;
    private String password;
    private LocalUserRole role;
  }
}
