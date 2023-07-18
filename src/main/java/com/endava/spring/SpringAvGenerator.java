package com.endava.spring;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import com.samskivert.mustache.Template.Fragment;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.callbacks.Callback;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.links.Link;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.parser.util.OpenAPIDeserializer;
import io.swagger.v3.parser.util.OpenAPIDeserializer.ParseResult;

import java.io.IOException;
import java.io.Writer;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.openapitools.codegen.*;
import org.openapitools.codegen.languages.SpringCodegen;

import java.util.*;

public class SpringAvGenerator extends SpringCodegen {

  private static final String VERSION_PREFIX = "v";
  private static final String VERSION_EXTENSION = "x-av-versions";
  private static final String FROM_VERSION = "from";
  private static final String TO_VERSION = "to";
  private static final String EXCLUDE_IN_VERSIONS = "exclude";
  private static final String REQUIRED = "required";
  private static final String DEFINITIONS_EXTENSION = "definitions";
  private final Set<Number> allSupportedVersions = new TreeSet<>(Comparator.comparing(number -> number.doubleValue()));
  private static final Set<String> excludedFields = new HashSet<>(Arrays.asList("tags", "extensions"));
  private static final ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(Include.NON_NULL);
  private static final OpenAPIDeserializer openAPIDeserializer = new OpenAPIDeserializer();
  private static final String EXTENSIONS = "extensions";
  private static final Pattern REF_PATTERN = Pattern.compile("#\\/components[^\"]*\"");
  private static final Number DEFAULT_VERSION = 1.0;
  private static String openAPIString;

  @Override
  public String getName() {
    return "spring-av";
  }

  @Override
  public void processOpts() {
    super.processOpts();
    setTemplateDir("spring-av");
    supportingFiles.add(new SupportingFile("springdoc.mustache",
        ("src.main.resources").replace(".", java.io.File.separator), "springdoc.properties"));
  }


  @Override
  protected ImmutableMap.Builder<String, Mustache.Lambda> addMustacheLambdas() {
    return super.addMustacheLambdas()
        .put("avPathMustache", new AvPathMustacheLambda())
        .put("avSpringDocMustache", new SpringDocMustacheLambda());
  }

  public static class AvPathMustacheLambda implements Mustache.Lambda {

    private static final Pattern VERSION_PATTERN = Pattern.compile("v\\d.?\\d?");

    @Override
    public void execute(Template.Fragment fragment, Writer writer) throws IOException {
      String path = fragment.execute();
      Matcher versionMatcher = VERSION_PATTERN.matcher(path);
      versionMatcher.find();
      String version = versionMatcher.group();
      String pathWithoutVersion = path.replace(version, "");
      String result = "/" + version + pathWithoutVersion;
      writer.write(result);
    }
  }

  public class SpringDocMustacheLambda implements Mustache.Lambda {

    @Override
    public void execute(Fragment fragment, Writer writer) throws IOException {
      int supportedVersionGroup = 0;
      String lineToModify = fragment.execute();
      for (Number version : allSupportedVersions) {
        writer.write(lineToModify.replace("[", "[" + supportedVersionGroup)
            .replace("v*", "v" + version));
        supportedVersionGroup++;
      }
    }
  }

  @Override
  public void preprocessOpenAPI(OpenAPI openAPI) {
    super.preprocessOpenAPI(openAPI);
    setOpenAPIString(openAPI);
    if (openAPI.getPaths() != null) {
      getAllSupportedVersions(openAPI);
      preprocessPathsAndSchemas(openAPI);
    }
  }

  private void preprocessPathsAndSchemas(OpenAPI openAPI) {
    Map<String, PathItem> processedPaths = new LinkedHashMap<>();
    Map<String, Schema> processedSchemas = new LinkedHashMap<>();
    Set<String> allRefs = getAllRefs(openAPIString);
    openAPI.setPaths(new Paths());
    openAPI.setComponents(new Components());

    allSupportedVersions.forEach(version -> {
      OpenAPI versionedOpenAPI = addVersioningToRefs(allRefs, version);
      addVersioningToSchemas(versionedOpenAPI, processedSchemas, version);
      addVersioningToPaths(versionedOpenAPI, processedPaths, processedSchemas, version);
      addVersioningToComponents(openAPI, versionedOpenAPI, version);
    });

    openAPI.getPaths().putAll(processedPaths);
    openAPI.getComponents().setSchemas(processedSchemas);
  }

  private Set<String> getAllRefs(String openAPIString) {
    Set<String> allRefs = new HashSet<>();
    Matcher refsMatcher = REF_PATTERN.matcher(openAPIString);
    while (refsMatcher.find()) {
      allRefs.add(refsMatcher.group());
    }
    return allRefs;
  }

  private void setOpenAPIString(OpenAPI openAPI) {
    try {
      openAPIString = mapper.writeValueAsString(openAPI);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private OpenAPI addVersioningToRefs(Set<String> allRefs, Number version) {
    String openAPIWithVersionedRefs = openAPIString;
    for (String ref : allRefs) {
      String versionedRef = new StringBuilder(ref).insert(ref.length() - 1, VERSION_PREFIX + version).toString();
      openAPIWithVersionedRefs = openAPIWithVersionedRefs.replace(ref, versionedRef);
    }
    try {
      return openAPIDeserializer.deserialize(mapper.readTree(openAPIWithVersionedRefs)).getOpenAPI();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private void addVersioningToComponents(OpenAPI openAPI, OpenAPI processedOpenAPI, Number version) {
    Map<String, Parameter> parameters = processedOpenAPI.getComponents().getParameters();
    if (parameters != null) {
      parameters.forEach((key, value) -> openAPI.getComponents().addParameters(key + VERSION_PREFIX + version, value));
    }

    Map<String, Example> examples = processedOpenAPI.getComponents().getExamples();
    if (examples != null) {
      examples.forEach((key, value) -> openAPI.getComponents().addExamples(key + VERSION_PREFIX + version, value));
    }

    Map<String, RequestBody> requestBodies = processedOpenAPI.getComponents().getRequestBodies();
    if (requestBodies != null) {
      requestBodies.forEach((key, value) -> openAPI.getComponents().addRequestBodies(key + VERSION_PREFIX + version, value));
    }

    Map<String, Header> headers = processedOpenAPI.getComponents().getHeaders();
    if (headers != null) {
      headers.forEach((key, value) -> openAPI.getComponents().addHeaders(key + VERSION_PREFIX + version, value));
    }

    Map<String, SecurityScheme> securitySchemes = processedOpenAPI.getComponents().getSecuritySchemes();
    if (securitySchemes != null) {
      securitySchemes.forEach((key, value) -> openAPI.getComponents().addSecuritySchemes(key + VERSION_PREFIX + version, value));
    }

    Map<String, Link> links = processedOpenAPI.getComponents().getLinks();
    if (links != null) {
      links.forEach((key, value) -> openAPI.getComponents().addLinks(key + VERSION_PREFIX + version, value));
    }

    Map<String, Callback> callbacks = processedOpenAPI.getComponents().getCallbacks();
    if (callbacks != null) {
      callbacks.forEach((key, value) -> openAPI.getComponents().addCallbacks(key + VERSION_PREFIX + version, value));
    }

    Map<String, Object> extensions = processedOpenAPI.getComponents().getExtensions();
    if (extensions != null) {
      extensions.forEach((key, value) -> openAPI.getComponents().addExtension(key + VERSION_PREFIX + version, value));
    }
  }

  private void addVersioningToPaths(OpenAPI openAPI, Map<String, PathItem> processedPaths, Map<String, Schema> processedSchemas, Number version) {
    for (Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
      String pathName = pathEntry.getKey();
      PathItem pathItem = pathEntry.getValue();
      for (Entry<HttpMethod, Operation> operationEntry : pathItem.readOperationsMap().entrySet()) {
        Operation operation = operationEntry.getValue();
        Map<String, Object> extensions = operation.getExtensions();
        if (isSupported(extensions, version)) {
          HttpMethod operationHttpMethod = operationEntry.getKey();
          Operation operationCopy = copyOperation(operation);

          Optional<Number> definitionVersion = getDefinitionVersion(version, extensions);
          if (definitionVersion.isPresent()) {
            ObjectNode operationDefinitionNode = copyFieldsFromDefinition(definitionVersion.get(), operationCopy);
            operationCopy = openAPIDeserializer.getOperation(operationDefinitionNode, "", new ParseResult());
          }

          processOperation(version, operationCopy, operation, processedSchemas, openAPI);
          String versionedPathName = getVersionedPathName(version, pathName);
          addOperationToPath(pathItem, processedPaths, versionedPathName, operationCopy, operationHttpMethod, version);
        }
      }
    }
  }

  private static String getVersionedPathName(Number version, String pathName) {
    int secondSlashIndex = pathName.indexOf("/", pathName.indexOf("/") + 1);
    int versionStartIndex = secondSlashIndex == -1 ? pathName.length() : secondSlashIndex;
    return new StringBuilder(pathName).insert(versionStartIndex, VERSION_PREFIX + version).toString();
  }

  private ObjectNode copyFieldsFromDefinition(Number version, Object objectToDefine) {
    ObjectNode nodeToDefine = mapper.convertValue(objectToDefine, ObjectNode.class);
    JsonNode definition = nodeToDefine.get(EXTENSIONS).get(VERSION_EXTENSION).get(DEFINITIONS_EXTENSION).get(String.valueOf(version));
    Iterator<String> definitionFieldsIter = definition.fieldNames();
    while (definitionFieldsIter.hasNext()) {
      String field = definitionFieldsIter.next();
      if (!excludedFields.contains(field)) {
        nodeToDefine.replace(field, definition.get(field));
      }
    }
    return nodeToDefine;
  }

  private Optional<Number> getDefinitionVersion(Number version, Map<String, Object> extensions) {
    if (extensions != null && extensions.get(VERSION_EXTENSION) != null) {
      Map<String, Object> versionsMap = (Map<String, Object>) extensions.get(VERSION_EXTENSION);
      if (versionsMap.containsKey(DEFINITIONS_EXTENSION)) {
        Map<String, Object> definitionsMap = (Map<String, Object>) versionsMap.get(DEFINITIONS_EXTENSION);
        List<Number> definitionsVersions = definitionsMap.keySet().stream().map(key -> parseNumber(key)).collect(Collectors.toList());
        return definitionsVersions.stream()
            .sorted(Collections.reverseOrder())
            .filter(definition -> definition.doubleValue() <= version.doubleValue())
            .findFirst();
      }
    }
    return Optional.empty();
  }

  private void processOperation(Number version, Operation operationCopy, Operation originalOperation, Map<String, Schema> processedSchemas, OpenAPI openAPI) {
    if (operationCopy.getParameters() == null && originalOperation.getParameters() != null) {
      List<Parameter> parameters = new ArrayList<>();
      for (Parameter param : originalOperation.getParameters()) {
        if (isSupported(param.getExtensions(), version)) {
          parameters.add(param);
        }
      }
      parameters.stream().forEach(param -> operationCopy.addParametersItem(param));
    }

    if (operationCopy.getResponses() != null) {
      for (Entry<String, ApiResponse> response : operationCopy.getResponses().entrySet()) {
        Optional<Number> definitionVersion = getDefinitionVersion(version, response.getValue().getExtensions());
        if (definitionVersion.isPresent()) {
          ObjectNode definitionNode = copyFieldsFromDefinition(definitionVersion.get(), response.getValue());
          ApiResponse definitionResponse = openAPIDeserializer.getResponse(definitionNode, "", new ParseResult());
          definitionResponse.getContent().values().stream()
              .map(MediaType::getSchema)
              .filter(schema -> schema.get$ref() == null)
              .forEach(schema -> {
                String schemaName = operationCopy.getOperationId() + "_" + response.getKey() + "_response" + VERSION_PREFIX + version;
                schema.set$ref("#/components/schemas/" + schemaName);
                processedSchemas.put(schemaName, schema);
              });
          response.setValue(definitionResponse);
        }
      }
    }

    if (operationCopy.getRequestBody() != null && operationCopy.getRequestBody().get$ref() != null) {
      String requestBodyRef = operationCopy.getRequestBody().get$ref();
      String requestBodyRefName = requestBodyRef.substring(requestBodyRef.lastIndexOf("/") + 1);
      RequestBody origRequestBody = openAPI.getComponents().getRequestBodies().entrySet()
          .stream().filter(entry -> (entry.getKey() + VERSION_PREFIX + version).equals(requestBodyRefName))
          .findFirst().map(Entry::getValue).get();

      Optional<Number> definitionVersion = getDefinitionVersion(version, origRequestBody.getExtensions());
      if (definitionVersion.isPresent()) {
        ObjectNode definitionNode = copyFieldsFromDefinition(definitionVersion.get(), origRequestBody);
        RequestBody definitionRequest = openAPIDeserializer.getRequestBody(definitionNode, "", new ParseResult());
        definitionRequest.getContent().values().stream()
            .map(MediaType::getSchema)
            .filter(schema -> schema.get$ref() == null)
            .forEach(schema -> {
              String schemaName = operationCopy.getOperationId() + "_request" + VERSION_PREFIX + version;
              schema.set$ref("#/components/schemas/" + schemaName);
              processedSchemas.put(schemaName, schema);
            });
        operationCopy.setRequestBody(definitionRequest);
      }
    }

    if (operationCopy.getOperationId() != null) {
      operationCopy.setOperationId(operationCopy.getOperationId() + VERSION_PREFIX + version);
    }
  }

  private Operation copyOperation(Operation operation) {
    ObjectNode operationNode = mapper.convertValue(operation, ObjectNode.class);
    operationNode.remove("parameters");
    return openAPIDeserializer.getOperation(operationNode, "", new ParseResult());
  }

  private PathItem copyPathItem(PathItem pathItem) {
    ObjectNode pathNode = mapper.convertValue(pathItem, ObjectNode.class);
    pathNode.remove("get");
    pathNode.remove("put");
    pathNode.remove("post");
    pathNode.remove("delete");
    pathNode.remove("options");
    pathNode.remove("head");
    pathNode.remove("patch");
    pathNode.remove("trace");
    pathNode.remove("parameters");
    return openAPIDeserializer.getPathItem(pathNode, "", new ParseResult());
  }

  private Schema copySchema(Schema schema) {
    ObjectNode schemaNode = mapper.convertValue(schema, ObjectNode.class);
    return openAPIDeserializer.getSchema(schemaNode, "", new ParseResult());
  }

  private void addOperationToPath(PathItem pathItem, Map<String, PathItem> processedPaths, String versionedPathName, Operation processedOp, HttpMethod operationHttpMethod, Number version) {
    if (processedPaths.get(versionedPathName) == null) {
      PathItem versionedPath = copyPathItem(pathItem);
      addPathParameters(pathItem, version, versionedPath);
      versionedPath.operation(operationHttpMethod, processedOp);
      processedPaths.put(versionedPathName, versionedPath);
    } else {
      processedPaths.get(versionedPathName).operation(operationHttpMethod, processedOp);
    }
  }

  private void addPathParameters(PathItem pathItem, Number version, PathItem versionedPath) {
    if (pathItem.getParameters() != null) {
      for (Parameter pathParam : pathItem.getParameters()) {
        Map<String, Object> pathParamExtensions = pathParam.getExtensions();
        if (isSupported(pathParamExtensions, version)) {
          versionedPath.addParametersItem(pathParam);
        }
      }
    }
  }

  private void addVersioningToSchemas(OpenAPI openAPI, Map<String, Schema> processedSchemas, Number version) {
    if (openAPI.getComponents().getSchemas() != null) {
      for (Entry<String, Schema> schemaEntry : openAPI.getComponents().getSchemas().entrySet()) {
        Schema<?> originalSchema = schemaEntry.getValue();
        Schema<?> processedSchema = copySchema(originalSchema);

        Optional<Double> definitionVersion = getDefinitionVersion(version, schemaEntry.getValue().getExtensions());
        if (definitionVersion.isPresent()) {
          ObjectNode definitionSchema = copyFieldsFromDefinition(definitionVersion.get(), processedSchema);
          processedSchema = openAPIDeserializer.getSchema(definitionSchema, "", new ParseResult());
        }
        if (processedSchema.getProperties() != null) {
          Map<String, Schema> processedProperties = new LinkedHashMap<>();
          for (Entry<String, Schema> property : processedSchema.getProperties().entrySet()) {
            String propertyName = property.getKey();
            Schema propertyValue = property.getValue();
            Map<String, Object> extensions = propertyValue.getExtensions();
            if (isSupported(extensions, version)) {
              processedProperties.put(propertyName, propertyValue);
              addRequiredFields(version, propertyName, extensions, processedSchema);
            }
          }
          processedSchema.setProperties(processedProperties);
        }
        processedSchemas.put(schemaEntry.getKey() + VERSION_PREFIX + version, processedSchema);
      }
    }
  }

  private boolean isSupported(Map<String, Object> extensions, Number version) {
    if (extensions != null && extensions.get(VERSION_EXTENSION) != null) {
      Map<String, Object> versionsMap = (Map<String, Object>) extensions.get(VERSION_EXTENSION);
      Number supportedFromVersion = (Number) versionsMap.get(FROM_VERSION);
      if (supportedFromVersion != null && supportedFromVersion.doubleValue() > version.doubleValue()) {
        return false;
      }
      Number supportedToVersion = (Number) versionsMap.get(TO_VERSION);
      if (supportedToVersion != null && supportedToVersion.doubleValue() < version.doubleValue()) {
        return false;
      }
      List<Number> excludeInVersion = (List<Number>) versionsMap.get(EXCLUDE_IN_VERSIONS);
      if (excludeInVersion != null && excludeInVersion.contains(version)) {
        return false;
      }
    }
    return true;
  }

  private void addRequiredFields(Number version, String propertyName, Map<String, Object> extensions, Schema processedSchema) {
    if (extensions != null && extensions.get(VERSION_EXTENSION) != null) {
      Map<String, Object> versionsMap = (Map<String, Object>) extensions.get(VERSION_EXTENSION);
      Set<String> requiredFields = new HashSet<>();
      if (processedSchema.getRequired() != null) {
        requiredFields.addAll(processedSchema.getRequired());
      }

      if (versionsMap.get(REQUIRED) != null) {
        processRequiredFields(version, requiredFields, propertyName, extensions);
      }
      processedSchema.setRequired(new ArrayList<>(requiredFields));
    }
  }

  private void processRequiredFields(Number version, Set<String> requiredFields, String propertyName, Map<String, Object> extensions) {
    Map<String, Object> requiredFieldVersionsMap = ((Map<String, LinkedHashMap<String, Object>>) extensions.get(VERSION_EXTENSION)).get(REQUIRED);
    Number requiredFieldFromVersion = (Number) requiredFieldVersionsMap.get(FROM_VERSION);
    Number requiredFieldToVersion = (Number) requiredFieldVersionsMap.get(TO_VERSION);
    requiredFields.add(propertyName);
    if (requiredFieldFromVersion != null && version.doubleValue() < requiredFieldFromVersion.doubleValue()) {
      requiredFields.remove(propertyName);
    }
    if (requiredFieldToVersion != null && version.doubleValue() > requiredFieldToVersion.doubleValue()) {
      requiredFields.remove(propertyName);
    }
    List<Number> excludeInVersion = (List<Number>) requiredFieldVersionsMap.get(EXCLUDE_IN_VERSIONS);
    if (excludeInVersion != null && excludeInVersion.contains(version)) {
      requiredFields.remove(propertyName);
    }
  }

  private void getAllSupportedVersions(OpenAPI openAPI) {
    Number defaultVersion = getDefaultVersion(openAPI);
    allSupportedVersions.add(defaultVersion);

    JsonNode openApiJson;
    try {
      openApiJson = mapper.readTree(openAPIString);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }

    List<JsonNode> versionsNodes = openApiJson.findValues(VERSION_EXTENSION);
    versionsNodes.stream().forEach(versionNode -> {
      JsonNode toVersionNode = versionNode.findValue(TO_VERSION);
      if (toVersionNode != null) allSupportedVersions.add(parseNumber(toVersionNode.toString()));

      JsonNode fromVersionNode = versionNode.findValue(FROM_VERSION);
      if (fromVersionNode != null) allSupportedVersions.add(parseNumber(fromVersionNode.toString()));

      JsonNode excludeInVersionsNode = versionNode.findValue(EXCLUDE_IN_VERSIONS);
      if (excludeInVersionsNode != null) {
        try {
          ObjectReader reader = mapper.readerFor(new TypeReference<List<Number>>() {});
          allSupportedVersions.addAll(reader.readValue(excludeInVersionsNode));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      JsonNode definitionsNode = versionNode.findValue(DEFINITIONS_EXTENSION);
      if (definitionsNode != null) definitionsNode.fieldNames().forEachRemaining(definitionKey -> allSupportedVersions.add(parseNumber(definitionKey)));
    });
  }

  private Number getDefaultVersion(OpenAPI openAPI) {
    Info info = openAPI.getInfo();
    if (info == null || info.getVersion() == null) {
      return DEFAULT_VERSION;
    }

    return parseNumber(info.getVersion());
  }

  private Number parseNumber(String stringNum) {
    Number number = null;
    if (stringNum.contains(".")) {
      number = Double.parseDouble(stringNum);
    } else {
      number = Integer.parseInt(stringNum);
    }
    return number;
  }
}
