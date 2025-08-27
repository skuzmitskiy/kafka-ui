package com.provectus.kafka.ui.service.masking.policies;

import com.provectus.kafka.ui.config.ClustersProperties;
import com.provectus.kafka.ui.exception.ValidationException;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

interface FieldsSelector {

  static FieldsSelector create(ClustersProperties.Masking property) {
    if (StringUtils.hasText(property.getFieldsNamePattern()) && !CollectionUtils.isEmpty(property.getFields())) {
      throw new ValidationException("You can't provide both fieldNames & fieldsNamePattern for masking");
    }
    
    boolean nestedPathsEnabled = Boolean.TRUE.equals(property.getEnableNestedPaths());
    
    if (StringUtils.hasText(property.getFieldsNamePattern())) {
      Pattern pattern = Pattern.compile(property.getFieldsNamePattern());
      return new FieldsSelector() {
        @Override
        public boolean shouldBeMasked(String fieldName) {
          return pattern.matcher(fieldName).matches();
        }
        
        @Override
        public boolean shouldBeMasked(List<String> fieldPath) {
          if (!nestedPathsEnabled) {
            return shouldBeMasked(fieldPath.get(fieldPath.size() - 1));
          }
          String path = String.join(".", fieldPath);
          return pattern.matcher(path).matches();
        }
      };
    }
    
    if (!CollectionUtils.isEmpty(property.getFields())) {
      return new FieldsSelector() {
        @Override
        public boolean shouldBeMasked(String fieldName) {
          return property.getFields().contains(fieldName);
        }
        
        @Override
        public boolean shouldBeMasked(List<String> fieldPath) {
          if (!nestedPathsEnabled) {
            return shouldBeMasked(fieldPath.get(fieldPath.size() - 1));
          }
          String path = String.join(".", fieldPath);
          return property.getFields().contains(path);
        }
      };
    }
    
    //no pattern, no field names - mean all fields should be masked
    return new FieldsSelector() {
      @Override
      public boolean shouldBeMasked(String fieldName) {
        return true;
      }
      
      @Override
      public boolean shouldBeMasked(List<String> fieldPath) {
        return true;
      }
    };
  }

  boolean shouldBeMasked(String fieldName);
  
  boolean shouldBeMasked(List<String> fieldPath);

}
