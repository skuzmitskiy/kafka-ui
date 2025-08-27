package com.provectus.kafka.ui.service.masking.policies;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.provectus.kafka.ui.config.ClustersProperties;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class NestedFieldsMaskingTest {

  @Test
  @SneakyThrows
  void testNestedFieldMaskingWithReplace() {
    var properties = new ClustersProperties.Masking();
    properties.setType(ClustersProperties.Masking.Type.REPLACE);
    properties.setReplacement("***MASKED***");
    properties.setFields(List.of("user.address.street", "user.email"));
    properties.setEnableNestedPaths(true);

    var policy = MaskingPolicy.create(properties);
    
    String inputJson = """
        {
          "user": {
            "name": "John Doe",
            "email": "john@example.com",
            "address": {
              "street": "123 Main St",
              "city": "Anytown",
              "zipcode": "12345"
            }
          },
          "order": {
            "id": "ORD-001",
            "total": 99.99
          }
        }
        """;

    String expectedJson = """
        {
          "user": {
            "name": "John Doe",
            "email": "***MASKED***",
            "address": {
              "street": "***MASKED***",
              "city": "Anytown",
              "zipcode": "12345"
            }
          },
          "order": {
            "id": "ORD-001",
            "total": 99.99
          }
        }
        """;

    JsonMapper mapper = new JsonMapper();
    ContainerNode<?> input = (ContainerNode<?>) mapper.readTree(inputJson);
    ContainerNode<?> expected = (ContainerNode<?>) mapper.readTree(expectedJson);
    
    ContainerNode<?> result = policy.applyToJsonContainer(input);
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @SneakyThrows
  void testNestedFieldMaskingWithRemove() {
    var properties = new ClustersProperties.Masking();
    properties.setType(ClustersProperties.Masking.Type.REMOVE);
    properties.setFields(List.of("user.sensitiveData", "metadata.internal"));
    properties.setEnableNestedPaths(true);

    var policy = MaskingPolicy.create(properties);
    
    String inputJson = """
        {
          "user": {
            "name": "John Doe",
            "sensitiveData": {
              "ssn": "123-45-6789",
              "creditCard": "4111-1111-1111-1111"
            },
            "publicInfo": "Available"
          },
          "metadata": {
            "timestamp": "2023-01-01",
            "internal": {
              "secret": "top-secret",
              "debug": "trace-info"
            }
          }
        }
        """;

    String expectedJson = """
        {
          "user": {
            "name": "John Doe",
            "publicInfo": "Available"
          },
          "metadata": {
            "timestamp": "2023-01-01"
          }
        }
        """;

    JsonMapper mapper = new JsonMapper();
    ContainerNode<?> input = (ContainerNode<?>) mapper.readTree(inputJson);
    ContainerNode<?> expected = (ContainerNode<?>) mapper.readTree(expectedJson);
    
    ContainerNode<?> result = policy.applyToJsonContainer(input);
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @SneakyThrows
  void testNestedFieldMaskingWithPattern() {
    var properties = new ClustersProperties.Masking();
    properties.setType(ClustersProperties.Masking.Type.MASK);
    properties.setMaskingCharsReplacement(List.of("X", "x", "n", "-"));
    properties.setFieldsNamePattern(".*\\.secret.*");
    properties.setEnableNestedPaths(true);

    var policy = MaskingPolicy.create(properties);
    
    String inputJson = """
        {
          "config": {
            "publicSetting": "visible",
            "secretKey": "my-secret-key",
            "database": {
              "host": "localhost",
              "secretPassword": "very-secret-password"
            }
          }
        }
        """;

    JsonMapper mapper = new JsonMapper();
    ContainerNode<?> input = (ContainerNode<?>) mapper.readTree(inputJson);
    
    ContainerNode<?> result = policy.applyToJsonContainer(input);
    
    // Verify that nested secret fields are masked
    JsonNode configNode = result.get("config");
    assertThat(configNode.get("publicSetting").asText()).isEqualTo("visible");
    assertThat(configNode.get("secretKey").asText()).isEqualTo("xx-xxxxxx-xxx"); // masked
    
    JsonNode databaseNode = configNode.get("database");
    assertThat(databaseNode.get("host").asText()).isEqualTo("localhost");
    assertThat(databaseNode.get("secretPassword").asText()).isEqualTo("xxxx-xxxxxx-xxxxxxxx"); // masked
  }

  @Test
  @SneakyThrows
  void testBackwardCompatibilityWithoutNestedPaths() {
    var properties = new ClustersProperties.Masking();
    properties.setType(ClustersProperties.Masking.Type.REPLACE);
    properties.setReplacement("***MASKED***");
    properties.setFields(List.of("email")); // Only top-level field names
    properties.setEnableNestedPaths(false); // Explicitly disabled

    var policy = MaskingPolicy.create(properties);
    
    String inputJson = """
        {
          "email": "john@example.com",
          "user": {
            "email": "nested@example.com"
          }
        }
        """;

    String expectedJson = """
        {
          "email": "***MASKED***",
          "user": {
            "email": "***MASKED***"
          }
        }
        """;

    JsonMapper mapper = new JsonMapper();
    ContainerNode<?> input = (ContainerNode<?>) mapper.readTree(inputJson);
    ContainerNode<?> expected = (ContainerNode<?>) mapper.readTree(expectedJson);
    
    ContainerNode<?> result = policy.applyToJsonContainer(input);
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @SneakyThrows
  void testArrayElementsWithNestedPaths() {
    var properties = new ClustersProperties.Masking();
    properties.setType(ClustersProperties.Masking.Type.REPLACE);
    properties.setReplacement("***MASKED***");
    properties.setFields(List.of("users.email"));
    properties.setEnableNestedPaths(true);

    var policy = MaskingPolicy.create(properties);
    
    String inputJson = """
        {
          "users": [
            {
              "name": "John",
              "email": "john@example.com"
            },
            {
              "name": "Jane",
              "email": "jane@example.com"
            }
          ]
        }
        """;

    JsonMapper mapper = new JsonMapper();
    ContainerNode<?> input = (ContainerNode<?>) mapper.readTree(inputJson);
    
    ContainerNode<?> result = policy.applyToJsonContainer(input);
    
    // Since we're using array indices in the path, this should only mask exact path matches
    // In this case, users.email doesn't match users.0.email or users.1.email
    // So emails should remain unmasked unless we specify the full path with indices
    JsonNode usersNode = result.get("users");
    assertThat(usersNode.get(0).get("email").asText()).isEqualTo("john@example.com");
    assertThat(usersNode.get(1).get("email").asText()).isEqualTo("jane@example.com");
  }
}
