package com.provectus.kafka.ui.controller;

import com.provectus.kafka.ui.api.ApplicationConfigApi;
import com.provectus.kafka.ui.config.ClustersProperties;
import com.provectus.kafka.ui.model.ApplicationConfigDTO;
import com.provectus.kafka.ui.model.ApplicationConfigPropertiesDTO;
import com.provectus.kafka.ui.model.ApplicationConfigValidationDTO;
import com.provectus.kafka.ui.model.ApplicationInfoDTO;
import com.provectus.kafka.ui.model.ApplicationPropertyValidationDTO;
import com.provectus.kafka.ui.model.ClusterConfigValidationDTO;
import com.provectus.kafka.ui.model.RestartRequestDTO;
import com.provectus.kafka.ui.model.UploadedFileInfoDTO;
import com.provectus.kafka.ui.service.ApplicationInfoService;
import com.provectus.kafka.ui.util.ApplicationRestarter;
import com.provectus.kafka.ui.util.DynamicConfigOperations;
import com.provectus.kafka.ui.util.DynamicConfigOperations.PropertiesStructure;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ApplicationConfigController extends AbstractController implements ApplicationConfigApi {

  private static final PropertiesMapper MAPPER = Mappers.getMapper(PropertiesMapper.class);

  @Mapper
  interface PropertiesMapper {

    PropertiesStructure fromDto(ApplicationConfigPropertiesDTO dto);

    ApplicationConfigPropertiesDTO toDto(PropertiesStructure propertiesStructure);

    default Properties map(Map<String, Object> map) {
      Properties properties = new Properties();
      if (map != null) {
        properties.putAll(map);
      }
      return properties;
    }

    default Map<String, Object> map(Properties properties) {
      Map<String, Object> map = new HashMap<>();
      if (properties != null) {
        properties.forEach((k, v) -> map.put(String.valueOf(k), v));
      }
      return map;
    }
  }

  private final DynamicConfigOperations dynamicConfigOperations;
  private final ApplicationRestarter restarter;
  private final ApplicationInfoService applicationInfoService;

  @Override
  public Mono<ResponseEntity<ApplicationInfoDTO>> getApplicationInfo(ServerWebExchange exchange) {
    return Mono.just(applicationInfoService.getApplicationInfo()).map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<ApplicationConfigDTO>> getCurrentConfig(ServerWebExchange exchange) {
    return Mono.fromSupplier(() -> ResponseEntity.ok(
        new ApplicationConfigDTO()
            .properties(MAPPER.toDto(dynamicConfigOperations.getCurrentProperties()))
    ));
  }

  @Override
  public Mono<ResponseEntity<Void>> restartWithConfig(Mono<RestartRequestDTO> restartRequestDto,
                                                      ServerWebExchange exchange) {
    return restartRequestDto
        .doOnNext(restartDto -> {
          var newConfig = MAPPER.fromDto(restartDto.getConfig().getProperties());
          dynamicConfigOperations.persist(newConfig);
        })
        .doOnSuccess(dto -> restarter.requestRestart())
        .map(dto -> ResponseEntity.ok().build());
  }

  @Override
  public Mono<ResponseEntity<UploadedFileInfoDTO>> uploadConfigRelatedFile(MultipartFile file,
                                                                           ServerWebExchange exchange) {
    return dynamicConfigOperations.uploadConfigRelatedFile(file)
        .map(path -> new UploadedFileInfoDTO().location(path.toString()))
        .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<ApplicationConfigValidationDTO>> validateConfig(Mono<ApplicationConfigDTO> configDto,
                                                                             ServerWebExchange exchange) {
    return configDto
        .flatMap(config -> {
          PropertiesStructure newConfig = MAPPER.fromDto(config.getProperties());
          ClustersProperties clustersProperties = newConfig.getKafka();
          return validateClustersConfig(clustersProperties)
              .map(validations -> new ApplicationConfigValidationDTO().clusters(validations));
        })
        .map(ResponseEntity::ok);
  }

  private Mono<Map<String, ClusterConfigValidationDTO>> validateClustersConfig(
      @Nullable ClustersProperties properties) {
    if (properties == null || properties.getClusters() == null) {
      return Mono.just(Map.of());
    }
    properties.validateAndSetDefaults();
    return Flux.fromIterable(properties.getClusters())
        .map(c -> Map.entry(c.getName(), new ClusterConfigValidationDTO()
            .kafka(new ApplicationPropertyValidationDTO().error(false))))
        .collectMap(Map.Entry::getKey, Map.Entry::getValue);
  }
}
