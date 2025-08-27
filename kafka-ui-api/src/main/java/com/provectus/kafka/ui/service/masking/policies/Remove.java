package com.provectus.kafka.ui.service.masking.policies;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.ObjectNode;


class Remove extends MaskingPolicy {

  Remove(FieldsSelector fieldsSelector) {
    super(fieldsSelector);
  }

  @Override
  public String applyToString(String str) {
    return "null";
  }

  @Override
  public ContainerNode<?> applyToJsonContainer(ContainerNode<?> node) {
    return (ContainerNode<?>) removeFields(node);
  }
  private JsonNode removeFields(JsonNode node) {
    return removeFields(node, new java.util.ArrayList<>());
  }

  private JsonNode removeFields(JsonNode node, java.util.List<String> path) {
    if (node.isObject()) {
      ObjectNode obj = ((ObjectNode) node).objectNode();
      node.fields().forEachRemaining(f -> {
        String fieldName = f.getKey();
        JsonNode fieldVal = f.getValue();
        
        java.util.List<String> currentPath = new java.util.ArrayList<>(path);
        currentPath.add(fieldName);
        
        if (!fieldShouldBeMasked(fieldName) && !fieldShouldBeMasked(currentPath)) {
          obj.set(fieldName, removeFields(fieldVal, currentPath));
        }
      });
      return obj;
    } else if (node.isArray()) {
      var arr = ((ArrayNode) node).arrayNode(node.size());
      int index = 0;
      for (JsonNode element : node) {
        java.util.List<String> currentPath = new java.util.ArrayList<>(path);
        currentPath.add(String.valueOf(index++));
        arr.add(removeFields(element, currentPath));
      }
      return arr;
    }
    return node;
  }
}
