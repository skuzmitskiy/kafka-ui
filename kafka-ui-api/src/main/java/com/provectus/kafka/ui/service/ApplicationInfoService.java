package com.provectus.kafka.ui.service;

import static com.provectus.kafka.ui.model.ApplicationInfoDTO.EnabledFeaturesEnum;

import com.provectus.kafka.ui.model.ApplicationInfoBuildDTO;
import com.provectus.kafka.ui.model.ApplicationInfoDTO;
import com.provectus.kafka.ui.util.DynamicConfigOperations;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.stereotype.Service;

@Service
public class ApplicationInfoService {

  private final DynamicConfigOperations dynamicConfigOperations;
  private final BuildProperties buildProperties;
  private final GitProperties gitProperties;

  public ApplicationInfoService(DynamicConfigOperations dynamicConfigOperations,
                                @Autowired(required = false) BuildProperties buildProperties,
                                @Autowired(required = false) GitProperties gitProperties) {
    this.dynamicConfigOperations = dynamicConfigOperations;
    this.buildProperties = Optional.ofNullable(buildProperties).orElse(new BuildProperties(new Properties()));
    this.gitProperties = Optional.ofNullable(gitProperties).orElse(new GitProperties(new Properties()));
  }

  public ApplicationInfoDTO getApplicationInfo() {
    return new ApplicationInfoDTO()
        .build(getBuildInfo())
        .enabledFeatures(getEnabledFeatures())
        .latestRelease(null);
  }

  private ApplicationInfoBuildDTO getBuildInfo() {
    return new ApplicationInfoBuildDTO()
        .isLatestRelease(false)
        .commitId(gitProperties.getShortCommitId())
        .version(buildProperties.getVersion())
        .buildTime(buildProperties.getTime() != null
            ? DateTimeFormatter.ISO_INSTANT.format(buildProperties.getTime()) : null);
  }

  private List<EnabledFeaturesEnum> getEnabledFeatures() {
    var enabledFeatures = new ArrayList<EnabledFeaturesEnum>();
    if (dynamicConfigOperations.dynamicConfigEnabled()) {
      enabledFeatures.add(EnabledFeaturesEnum.DYNAMIC_CONFIG);
    }
    return enabledFeatures;
  }

}
