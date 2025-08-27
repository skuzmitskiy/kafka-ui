package com.provectus.kafka.ui.service.masking.policies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.provectus.kafka.ui.config.ClustersProperties;
import com.provectus.kafka.ui.exception.ValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class FieldsSelectorTest {

  @Test
  void selectsFieldsDueToProvidedPattern() {
    var properties = new ClustersProperties.Masking();
    properties.setFieldsNamePattern("f1|f2");

    var selector = FieldsSelector.create(properties);
    assertThat(selector.shouldBeMasked("f1")).isTrue();
    assertThat(selector.shouldBeMasked("f2")).isTrue();
    assertThat(selector.shouldBeMasked("doesNotMatchPattern")).isFalse();
  }

  @Test
  void selectsFieldsDueToProvidedFieldNames() {
    var properties = new ClustersProperties.Masking();
    properties.setFields(List.of("f1", "f2"));

    var selector = FieldsSelector.create(properties);
    assertThat(selector.shouldBeMasked("f1")).isTrue();
    assertThat(selector.shouldBeMasked("f2")).isTrue();
    assertThat(selector.shouldBeMasked("notInAList")).isFalse();
  }

  @Test
  void selectAllFieldsIfNoPatternAndNoNamesProvided() {
    var properties = new ClustersProperties.Masking();

    var selector = FieldsSelector.create(properties);
    assertThat(selector.shouldBeMasked("anyPropertyName")).isTrue();
  }

  @Test
  void throwsExceptionIfBothFieldListAndPatternProvided() {
    var properties = new ClustersProperties.Masking();
    properties.setFieldsNamePattern("f1|f2");
    properties.setFields(List.of("f3", "f4"));

    assertThatThrownBy(() -> FieldsSelector.create(properties))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void selectsNestedFieldsWhenEnabledWithFieldNames() {
    var properties = new ClustersProperties.Masking();
    properties.setFields(List.of("user.email", "user.address.street"));
    properties.setEnableNestedPaths(true);

    var selector = FieldsSelector.create(properties);
    
    // Test single field names (backward compatibility)
    assertThat(selector.shouldBeMasked("email")).isFalse();
    assertThat(selector.shouldBeMasked("street")).isFalse();
    
    // Test nested paths
    assertThat(selector.shouldBeMasked(List.of("user", "email"))).isTrue();
    assertThat(selector.shouldBeMasked(List.of("user", "address", "street"))).isTrue();
    assertThat(selector.shouldBeMasked(List.of("user", "name"))).isFalse();
    assertThat(selector.shouldBeMasked(List.of("other", "email"))).isFalse();
  }

  @Test
  void selectsNestedFieldsWhenEnabledWithPattern() {
    var properties = new ClustersProperties.Masking();
    properties.setFieldsNamePattern("user\\..*|.*\\.secret");
    properties.setEnableNestedPaths(true);

    var selector = FieldsSelector.create(properties);
    
    // Test nested paths with pattern
    assertThat(selector.shouldBeMasked(List.of("user", "email"))).isTrue();
    assertThat(selector.shouldBeMasked(List.of("user", "address", "street"))).isTrue();
    assertThat(selector.shouldBeMasked(List.of("config", "secret"))).isTrue();
    assertThat(selector.shouldBeMasked(List.of("other", "public"))).isFalse();
  }

  @Test
  void fallsBackToFieldNameWhenNestedPathsDisabled() {
    var properties = new ClustersProperties.Masking();
    properties.setFields(List.of("user.email", "street"));
    properties.setEnableNestedPaths(false);

    var selector = FieldsSelector.create(properties);
    
    // Should only match the last part of the path when nested paths are disabled
    assertThat(selector.shouldBeMasked(List.of("user", "email"))).isFalse(); // Only matches "email", not "user.email"
    assertThat(selector.shouldBeMasked(List.of("address", "street"))).isTrue(); // Matches "street"
    assertThat(selector.shouldBeMasked("street")).isTrue(); // Direct field name matching still works
  }

  @Test
  void defaultNestedPathsBehaviorIsFalse() {
    var properties = new ClustersProperties.Masking();
    properties.setFields(List.of("user.email"));
    // enableNestedPaths not set, should default to false

    var selector = FieldsSelector.create(properties);
    
    // Should behave as if nested paths are disabled
    assertThat(selector.shouldBeMasked(List.of("user", "email"))).isFalse();
    assertThat(selector.shouldBeMasked("email")).isFalse();
  }

}
