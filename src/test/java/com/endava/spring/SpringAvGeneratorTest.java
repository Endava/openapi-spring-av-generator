package com.endava.spring;

import static java.util.stream.Collectors.groupingBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openapitools.codegen.TestUtils.assertFileContains;
import static org.openapitools.codegen.TestUtils.assertFileNotContains;
import static org.openapitools.codegen.TestUtils.assertFileNotExists;
import static org.openapitools.codegen.languages.AbstractJavaCodegen.IMPLICIT_HEADERS;
import static org.openapitools.codegen.languages.AbstractJavaCodegen.OPENAPI_NULLABLE;
import static org.openapitools.codegen.languages.SpringCodegen.*;
import static org.openapitools.codegen.languages.SpringCodegen.SKIP_DEFAULT_INTERFACE;
import static org.openapitools.codegen.languages.SpringCodegen.USE_TAGS;
import static org.openapitools.codegen.languages.features.DocumentationProviderFeatures.ANNOTATION_LIBRARY;
import static org.openapitools.codegen.languages.features.DocumentationProviderFeatures.DOCUMENTATION_PROVIDER;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import com.google.common.collect.ImmutableMap;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.parser.core.models.ParseOptions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.ClientOptInput;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.TestUtils;
import org.openapitools.codegen.config.CodegenConfigurator;
import org.openapitools.codegen.config.GlobalSettings;
import org.openapitools.codegen.JavaFileAssert;
import org.openapitools.codegen.languages.AbstractJavaCodegen;
import org.openapitools.codegen.languages.SpringCodegen;
import org.openapitools.codegen.languages.features.BeanValidationFeatures;
import org.openapitools.codegen.languages.features.CXFServerFeatures;
import org.openapitools.codegen.languages.features.DocumentationProviderFeatures;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

public class SpringAvGeneratorTest {

  /**
   * Define documentation providers to test
   */
  private final static String SPRINGFOX = "springfox";
  private final static String SPRINGFOX_DESTINATIONFILE = "SpringFoxConfiguration.java";
  private final static String SPRINGFOX_TEMPLATEFILE = "openapiDocumentationConfig.mustache";
  private final static String SPRINGDOC = "springdoc";
  private final static String SPRINGDOC_DESTINATIONFILE = "SpringDocConfiguration.java";
  private final static String SPRINGDOC_TEMPLATEFILE = "springdocDocumentationConfig.mustache";

  @Test
  public void clientOptsUnicity() {
    SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.cliOptions()
        .stream()
        .collect(groupingBy(CliOption::getOpt))
        .forEach((k, v) -> assertEquals(v.size(), 1, k + " is described multiple times"));
  }

  @Test
  public void doAnnotateDatesOnModelParameters() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/spring/3_0/issue_5436.yml", SPRING_BOOT, false);

    DefaultGenerator generator = new DefaultGenerator();

    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");
    generator.opts(configurationPair.getKey()).generate();

    JavaFileAssert.assertThat(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Zebrasv10Api.java"))
        .assertTypeAnnotations()
        .hasSize(3)
        .containsWithName("Validated")
        .containsWithName("Generated")
        .containsWithNameAndAttributes("Generated", ImmutableMap.of(
            "value", "\"com.endava.spring.SpringAvGenerator\""
        ))
        .containsWithNameAndAttributes("Tag", ImmutableMap.of(
            "name", "\"zebrasv1.0\""
        ))
        .toType()
        .assertMethod("getZebrasv10")
        .hasReturnType("ResponseEntity<Void>")
        .assertMethodAnnotations()
        .hasSize(2)
        .containsWithNameAndAttributes("Operation", ImmutableMap.of("operationId", "\"getZebrasv10\""))
        .containsWithNameAndAttributes("RequestMapping", ImmutableMap.of(
            "method", "RequestMethod.GET",
            "value", "\"/v1.0/zebras\""
        ))
        .toMethod()
        .hasParameter("limit").withType("BigDecimal")
        .assertParameterAnnotations()
        .containsWithName("Valid")
        .containsWithNameAndAttributes("Parameter", ImmutableMap.of("name", "\"limit\""))
        .containsWithNameAndAttributes("RequestParam", ImmutableMap.of("required", "false", "value", "\"limit\""))
        .toParameter()
        .toMethod()
        .hasParameter("animalParams").withType("AnimalParamsv10")
        .toMethod()
        .commentContainsLines("GET /v1.0/zebras", "@param limit (optional)")
        .bodyContainsLines("return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED)");

    JavaFileAssert.assertThat(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/AnimalParamsv10.java"))
        .hasImports("org.springframework.format.annotation.DateTimeFormat")
        .hasProperty("born").withType("LocalDate")
        .assertPropertyAnnotations()
        .containsWithNameAndAttributes("DateTimeFormat", ImmutableMap.of("iso", "DateTimeFormat.ISO.DATE"))
        .toProperty()
        .toType()
        .hasProperty("lastSeen").withType("OffsetDateTime")
        .assertPropertyAnnotations()
        .containsWithNameAndAttributes("DateTimeFormat", ImmutableMap.of("iso", "DateTimeFormat.ISO.DATE_TIME"))
        .toProperty().toType()
        .assertMethod("born", "LocalDate")
        .bodyContainsLines("this.born = born")
        .doesNotHaveComment();
  }

  @Test
  public void doGenerateCookieParams() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/spring/3_0/issue_5386.yaml", SPRING_BOOT, false);

    DefaultGenerator generator = new DefaultGenerator();

    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");
    generator.opts(configurationPair.getKey()).generate();

    JavaFileAssert.assertThat(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Elephantsv10Api.java"))
        .assertMethod("getElephantsv10", "String", "BigDecimal")
        .hasParameter("userToken")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("CookieValue", ImmutableMap.of("name", "\"userToken\""));

    JavaFileAssert.assertThat(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Zebrasv10Api.java"))
        .assertMethod("getZebrasv10", "String")
        .hasParameter("userToken")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("CookieValue", ImmutableMap.of("name", "\"userToken\""));

    JavaFileAssert.assertThat(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Birdsv10Api.java"))
        .assertMethod("getBirdsv10", "BigDecimal")
        .doesNotHaveParameter("userToken")
        .noneOfParameterHasAnnotation("CookieValue");
  }

  @Test
  public void doGenerateRequestParamForSimpleParam() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/spring/3_0/issue_3248.yaml", SPRING_BOOT, false);

    DefaultGenerator generator = new DefaultGenerator();

    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");

    generator.opts(configurationPair.getKey()).generate();

    assertFileContains(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Monkeysv10Api.java"), "@RequestParam");
    assertFileContains(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Elephantsv10Api.java"), "@RequestParam");
    assertFileContains(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Zebrasv10Api.java"), "@RequestParam");
    assertFileContains(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Bearsv10Api.java"), "@RequestParam");
    assertFileContains(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Camelsv10Api.java"), "@RequestParam");
    assertFileContains(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Pandasv10Api.java"), "@RequestParam");
    assertFileContains(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Crocodilesv10Api.java"), "@RequestParam");
    assertFileContains(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/PolarBearsv10Api.java"), "@RequestParam");
  }

  @Test
  public void doNotGenerateRequestParamForObjectQueryParam() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/spring/3_0/objectQueryParam.yaml", SPRING_BOOT, false);

    DefaultGenerator generator = new DefaultGenerator();

    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");

    generator.opts(configurationPair.getKey()).generate();

    assertFileNotContains(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Ponyv10Api.java"), "@RequestParam");
  }

  @Test
  public void generateFormatForDateAndDateTimeQueryParam() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/spring/3_0/issue_2053.yaml", SPRING_BOOT, false);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");
    generator.opts(configurationPair.getKey()).generate();

    JavaFileAssert.assertThat(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Elephantsv10Api.java"))
        .hasImports("org.springframework.format.annotation.DateTimeFormat")
        .assertMethod("getElephantsv10", "LocalDate")
        .hasParameter("startDate")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("DateTimeFormat", ImmutableMap.of("iso", "DateTimeFormat.ISO.DATE"));

    JavaFileAssert.assertThat(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Zebrasv10Api.java"))
        .hasImports("org.springframework.format.annotation.DateTimeFormat")
        .assertMethod("getZebrasv10", "OffsetDateTime")
        .hasParameter("startDateTime")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("DateTimeFormat", ImmutableMap.of("iso", "DateTimeFormat.ISO.DATE_TIME"));
  }

  @Test
  public void interfaceDefaultImplDisableWithResponseWrapper() {
    final SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.additionalProperties().put(RESPONSE_WRAPPER, "aWrapper");
    codegen.processOpts();

    // jdk8 tag has been removed
    Assert.assertEquals(codegen.additionalProperties().get("jdk8"), null);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void reactiveRequiredSpringBoot() {
    final SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.additionalProperties().put(SpringCodegen.REACTIVE, true);
    codegen.additionalProperties().put(CodegenConstants.LIBRARY, "spring-cloud");
    codegen.processOpts();
  }

  @Test
  public void shouldGenerateRequestParamForRefParams_3248_Regression() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/spring/3_0/3248-regression.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();

    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");

    generator.opts(configurationPair.getKey()).generate();

    JavaFileAssert.assertThat(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Examplev01Api.java"))
        .assertMethod("examplev01ApiGet", "String", "Formatv01")
        .hasParameter("query")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("RequestParam", ImmutableMap.of("value", "\"query\""))
        .toParameter().toMethod()
        .hasParameter("format")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("RequestParam", ImmutableMap.of("value", "\"format\""));
  }

  @Test
  public void shouldGenerateRequestParamForRefParams_3248_RegressionDates() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/spring/3_0/3248-regression-dates.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();

    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");

    generator.opts(configurationPair.getKey()).generate();

    JavaFileAssert.assertThat(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Examplev01Api.java"))
        .assertMethod("examplev01ApiGet", "OffsetDateTime")
        .hasParameter("start")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("RequestParam", ImmutableMap.of("value", "\"start\""))
        .containsWithNameAndAttributes("DateTimeFormat", ImmutableMap.of("iso", "DateTimeFormat.ISO.DATE_TIME"));
  }

  @Test
  public void springcloudWithAsyncAndJava8HasResponseWrapperCompletableFuture() {
    final SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.additionalProperties().put(SpringCodegen.ASYNC, true);
    codegen.additionalProperties().put(CodegenConstants.LIBRARY, "spring-cloud");
    codegen.processOpts();

    Assert.assertEquals(codegen.additionalProperties().get("jdk8-default-interface"), false);
    Assert.assertEquals(codegen.additionalProperties().get(RESPONSE_WRAPPER), "CompletableFuture");
  }

  @Test
  public void springcloudWithJava8DisableJdk8() {
    final SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.additionalProperties().put(CodegenConstants.LIBRARY, "spring-cloud");
    codegen.processOpts();

    Assert.assertEquals(codegen.additionalProperties().get("jdk8-default-interface"), false);
  }

  @Test
  public void testAdditionalPropertiesPutForConfigValues() throws Exception {
    final SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.additionalProperties().put(CodegenConstants.HIDE_GENERATION_TIMESTAMP, "true");
    codegen.additionalProperties().put(CodegenConstants.MODEL_PACKAGE, "xyz.yyyyy.mmmmm.model");
    codegen.additionalProperties().put(CodegenConstants.API_PACKAGE, "xyz.yyyyy.aaaaa.api");
    codegen.additionalProperties().put(CodegenConstants.INVOKER_PACKAGE, "xyz.yyyyy.iiii.invoker");
    codegen.additionalProperties().put(SpringCodegen.BASE_PACKAGE, "xyz.yyyyy.bbbb.base");
    codegen.additionalProperties().put(SpringCodegen.CONFIG_PACKAGE, "xyz.yyyyy.cccc.config");
    codegen.additionalProperties().put(SpringCodegen.SERVER_PORT, "8088");
    codegen.processOpts();

    OpenAPI openAPI = new OpenAPI();
    openAPI.addServersItem(new Server().url("https://api.abcde.xy:8082/v2"));
    openAPI.setInfo(new Info());
    openAPI.getInfo().setTitle("Some test API");
    codegen.preprocessOpenAPI(openAPI);

    Assert.assertEquals(codegen.additionalProperties().get(CodegenConstants.HIDE_GENERATION_TIMESTAMP), Boolean.TRUE);
    Assert.assertEquals(codegen.isHideGenerationTimestamp(), true);
    Assert.assertEquals(codegen.modelPackage(), "xyz.yyyyy.mmmmm.model");
    Assert.assertEquals(codegen.additionalProperties().get(CodegenConstants.MODEL_PACKAGE), "xyz.yyyyy.mmmmm.model");
    Assert.assertEquals(codegen.apiPackage(), "xyz.yyyyy.aaaaa.api");
    Assert.assertEquals(codegen.additionalProperties().get(CodegenConstants.API_PACKAGE), "xyz.yyyyy.aaaaa.api");
    Assert.assertEquals(codegen.getInvokerPackage(), "xyz.yyyyy.iiii.invoker");
    Assert.assertEquals(codegen.additionalProperties().get(CodegenConstants.INVOKER_PACKAGE), "xyz.yyyyy.iiii.invoker");
    Assert.assertEquals(codegen.getBasePackage(), "xyz.yyyyy.bbbb.base");
    Assert.assertEquals(codegen.additionalProperties().get(SpringCodegen.BASE_PACKAGE), "xyz.yyyyy.bbbb.base");
    Assert.assertEquals(codegen.getConfigPackage(), "xyz.yyyyy.cccc.config");
    Assert.assertEquals(codegen.additionalProperties().get(SpringCodegen.CONFIG_PACKAGE), "xyz.yyyyy.cccc.config");
    Assert.assertEquals(codegen.additionalProperties().get(SpringCodegen.TITLE), "someTest");
    Assert.assertEquals(codegen.additionalProperties().get(SpringCodegen.SERVER_PORT), "8088");
  }

  @Test
  public void testDefaultValuesFixed() {
    // we had an issue where int64, float, and double values were having single character string suffixes
    // included in their defaultValues
    // This test verifies that those characters are no longer present
    final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/spring.av/spring/2_0/issue1226.yaml");
    final SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setOpenAPI(openAPI);

    String int64Val = "9223372036854775807l";
    String floatVal = "3.14159f";
    String doubleVal = "3.14159d";

    // make sure that the model properties include character suffixes
    String modelName = "NumberHolder";
    Schema nhSchema = openAPI.getComponents().getSchemas().get(modelName);
    CodegenModel cm = codegen.fromModel(modelName, nhSchema);
    CodegenProperty int64Prop = cm.vars.get(0);
    CodegenProperty floatProp = cm.vars.get(1);
    CodegenProperty doubleProp = cm.vars.get(2);
    Assert.assertEquals(int64Prop.defaultValue, int64Val);
    Assert.assertEquals(floatProp.defaultValue, floatVal);
    Assert.assertEquals(doubleProp.defaultValue, doubleVal);

    int64Val = "9223372036854775807";
    floatVal = "3.14159";
    doubleVal = "3.14159";

    // make sure that the operation parameters omit character suffixes
    String route = "/numericqueryparams";
    Operation op = openAPI.getPaths().get(route).getGet();
    CodegenOperation co = codegen.fromOperation(route, "GET", op, null);
    CodegenParameter int64Param = co.queryParams.get(0);
    CodegenParameter floatParam = co.queryParams.get(1);
    CodegenParameter doubleParam = co.queryParams.get(2);
    Assert.assertEquals(int64Param.defaultValue, int64Val);
    Assert.assertEquals(floatParam.defaultValue, floatVal);
    Assert.assertEquals(doubleParam.defaultValue, doubleVal);
  }

  @Test
  public void testDoGenerateRequestBodyRequiredAttribute_3134_Regression() throws Exception {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/spring/3_0/3134-regression.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();

    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");

    generator.opts(configurationPair.getKey()).generate();

    JavaFileAssert.assertThat(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Examplev01Api.java"))
        .assertMethod("examplev01ApiPost", "ExampleApiPostRequestv01")
        .hasParameter("exampleApiPostRequestv01")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("RequestBody", ImmutableMap.of("required", "false"));

    assertFileContains(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Examplev01Api.java"),
        "@RequestBody(required = false");
  }

  @Test
  public void testInitialConfigValues() throws Exception {
    final SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.processOpts();

    OpenAPI openAPI = new OpenAPI();
    openAPI.addServersItem(new Server().url("https://api.abcde.xy:8082/v2"));
    openAPI.setInfo(new Info());
    codegen.preprocessOpenAPI(openAPI);

    Assert.assertEquals(codegen.additionalProperties().get(CodegenConstants.HIDE_GENERATION_TIMESTAMP), Boolean.FALSE);
    Assert.assertEquals(codegen.isHideGenerationTimestamp(), false);
    Assert.assertEquals(codegen.modelPackage(), "org.openapitools.model");
    Assert.assertEquals(codegen.additionalProperties().get(CodegenConstants.MODEL_PACKAGE), "org.openapitools.model");
    Assert.assertEquals(codegen.apiPackage(), "org.openapitools.api");
    Assert.assertEquals(codegen.additionalProperties().get(CodegenConstants.API_PACKAGE), "org.openapitools.api");
    Assert.assertEquals(codegen.getInvokerPackage(), "org.openapitools.api");
    Assert.assertEquals(codegen.additionalProperties().get(CodegenConstants.INVOKER_PACKAGE), "org.openapitools.api");
    Assert.assertEquals(codegen.getBasePackage(), "org.openapitools");
    Assert.assertEquals(codegen.additionalProperties().get(SpringCodegen.BASE_PACKAGE), "org.openapitools");
    Assert.assertEquals(codegen.getConfigPackage(), "org.openapitools.configuration");
    Assert.assertEquals(codegen.additionalProperties().get(SpringCodegen.CONFIG_PACKAGE), "org.openapitools.configuration");
    Assert.assertEquals(codegen.additionalProperties().get(SpringCodegen.SERVER_PORT), "8082");
    Assert.assertEquals(codegen.additionalProperties().get(SpringCodegen.UNHANDLED_EXCEPTION_HANDLING), false);
    Assert.assertEquals(codegen.additionalProperties().get(SpringCodegen.USE_RESPONSE_ENTITY), true);
  }

  @Test
  public void testMultipartBoot() throws IOException {
    final SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setLibrary("spring-boot");
    codegen.setDelegatePattern(true);
    codegen.additionalProperties().put(DOCUMENTATION_PROVIDER, "springfox");

    final Map<String, File> files = generateFiles(codegen, "src/test/resources/spring.av/spring/3_0/form-multipart-binary-array.yaml");

    // Check that the delegate handles the array
    JavaFileAssert.assertThat(files.get("MultipartArrayv10ApiDelegate.java"))
        .assertMethod("multipartArrayv10", "List<MultipartFile>")
        .hasParameter("files").withType("List<MultipartFile>");

    // Check that the api handles the array
    JavaFileAssert.assertThat(files.get("MultipartArrayv10Api.java"))
        .assertMethod("multipartArrayv10", "List<MultipartFile>")
        .hasParameter("files").withType("List<MultipartFile>")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("ApiParam", ImmutableMap.of("value", "\"Many files\""))
        .containsWithNameAndAttributes("RequestPart", ImmutableMap.of("value", "\"files\"", "required", "false"));

    // UPDATE: the following test has been ignored due to https://github.com/OpenAPITools/openapi-generator/pull/11081/
    // We will contact the contributor of the following test to see if the fix will break their use cases and
    // how we can fix it accordingly.
    //// Check that the delegate handles the single file
    // final File multipartSingleApiDelegate = files.get("MultipartSingleApiDelegate.java");
    // assertFileContains(multipartSingleApiDelegate.toPath(), "MultipartFile file");

    // Check that the api handles the single file
    JavaFileAssert.assertThat(files.get("MultipartSinglev10Api.java"))
        .assertMethod("multipartSinglev10", "MultipartFile")
        .hasParameter("file").withType("MultipartFile")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("ApiParam", ImmutableMap.of("value", "\"One file\""))
        .containsWithNameAndAttributes("RequestPart", ImmutableMap.of("value", "\"file\"", "required", "false"));

    // Check that api validates mixed multipart request
    JavaFileAssert.assertThat(files.get("MultipartMixedv10Api.java"))
        .assertMethod("multipartMixedv10", "MultipartMixedStatusv10", "MultipartFile", "MultipartMixedRequestMarkerv10",
            "List<MultipartMixedStatusv10>")
        .hasParameter("status").withType("MultipartMixedStatusv10")
        .assertParameterAnnotations()
        .containsWithName("Valid")
        .containsWithNameAndAttributes("ApiParam", ImmutableMap.of("value", "\"\""))
        .containsWithNameAndAttributes("RequestParam", ImmutableMap.of("value", "\"status\"", "required", "true"))
        .toParameter().toMethod()
        .hasParameter("file").withType("MultipartFile")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("RequestPart", ImmutableMap.of("value", "\"file\"", "required", "true"))
        .toParameter().toMethod()
        .hasParameter("marker").withType("MultipartMixedRequestMarkerv10")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("RequestPart", ImmutableMap.of("value", "\"marker\"", "required", "false"))
        .toParameter().toMethod()
        .hasParameter("statusArray").withType("List<MultipartMixedStatusv10>")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("RequestPart", ImmutableMap.of("value", "\"statusArray\"", "required", "false"));
  }

  @Test
  public void shouldAddParameterWithInHeaderWhenImplicitHeadersIsTrue_issue14418() throws IOException {
    File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();

    OpenAPI openAPI = new OpenAPIParser()
        .readLocation("src/test/resources/spring.av/spring/3_0/bugs/issue_14418.yaml", null, new ParseOptions()).getOpenAPI();
    SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setLibrary(SPRING_BOOT);
    codegen.setOutputDir(output.getAbsolutePath());
    codegen.additionalProperties().put(SpringCodegen.INTERFACE_ONLY, "true");
    codegen.additionalProperties().put(SpringCodegen.USE_BEANVALIDATION, "true");
    codegen.additionalProperties().put(SpringCodegen.PERFORM_BEANVALIDATION, "true");
    codegen.additionalProperties().put(CodegenConstants.MODEL_PACKAGE, "xyz.model");
    codegen.additionalProperties().put(CodegenConstants.API_PACKAGE, "xyz.controller");
    codegen.additionalProperties().put(SpringCodegen.IMPLICIT_HEADERS, "true");

    ClientOptInput input = new ClientOptInput()
        .openAPI(openAPI)
        .config(codegen);

    DefaultGenerator generator = new DefaultGenerator();
    Map<String, File> files = generator.opts(input).generate().stream()
        .collect(Collectors.toMap(File::getName, Function.identity()));

    JavaFileAssert.assertThat(files.get("Testv10Api.java"))
        .isInterface()
        .hasImports("io.swagger.v3.oas.annotations.enums.ParameterIn")
        .assertMethod("testv10")
        .assertMethodAnnotations()
        .containsWithNameAndAttributes("Parameters", ImmutableMap.of(
            "value", "{ @Parameter(name = \"testHeader\", description = \"Test header\", required = true, in = ParameterIn.HEADER) }"
            // in = ParameterIn.HEADER is missing?!
        ));
  }

  @Test
  public void shouldAddValidAnnotationIntoCollectionWhenBeanValidationIsEnabled_issue14723() throws IOException {
    File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();

    OpenAPI openAPI = new OpenAPIParser()
        .readLocation("src/test/resources/spring.av/spring/3_0/bugs/issue_14723.yaml", null, new ParseOptions()).getOpenAPI();
    SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setLibrary(SPRING_CLOUD_LIBRARY);
    codegen.setOutputDir(output.getAbsolutePath());
    codegen.additionalProperties().put(SpringCodegen.USE_BEANVALIDATION, "true");
    codegen.additionalProperties().put(SpringCodegen.PERFORM_BEANVALIDATION, "true");
    codegen.additionalProperties().put(CodegenConstants.MODEL_PACKAGE, "xyz.model");
    codegen.additionalProperties().put(CodegenConstants.API_PACKAGE, "xyz.controller");

    ClientOptInput input = new ClientOptInput()
        .openAPI(openAPI)
        .config(codegen);

    DefaultGenerator generator = new DefaultGenerator();
    Map<String, File> files = generator.opts(input).generate().stream()
        .collect(Collectors.toMap(File::getName, Function.identity()));

    JavaFileAssert.assertThat(files.get("ResponseTestv10.java"))
        .isNormalClass()
        .hasImports("javax.validation.Valid")
        .hasProperty("details")
        .withType("Map<String, Object>")
        .toType()
        .hasProperty("response")
        .withType("JsonNullable<Set<ResponseTest2v10>>")
        .toType()
        .hasProperty("nullableDtos")
        .withType("JsonNullable<Set<@Valid ResponseTest2v10>>")
        .toType()
        .hasProperty("dtos")
        .withType("Set<@Valid ResponseTest2v10>")
        .toType()
        .hasProperty("listNullableDtos")
        .withType("JsonNullable<List<@Valid ResponseTest2v10>>")
        .toType()
        .hasProperty("listDtos")
        .withType("List<@Valid ResponseTest2v10>")
        .toType()
        .hasProperty("nullableStrings")
        .withType("JsonNullable<Set<String>>")
        .toType()
        .hasProperty("strings")
        .withType("Set<String>")
        .toType()
        .hasProperty("nullableInts")
        .withType("JsonNullable<Set<Integer>>")
        .toType()
        .hasProperty("ints")
        .withType("Set<Integer>");
  }

  // Helper function, intended to reduce boilerplate
  private Map<String, File> generateFiles(SpringCodegen codegen, String filePath) throws IOException {
    final File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();
    final String outputPath = output.getAbsolutePath().replace('\\', '/');

    codegen.setOutputDir(output.getAbsolutePath());
    codegen.additionalProperties().put(CXFServerFeatures.LOAD_TEST_DATA_FROM_FILE, "true");

    final ClientOptInput input = new ClientOptInput();
    final OpenAPI openAPI = new OpenAPIParser().readLocation(filePath, null, new ParseOptions()).getOpenAPI();
    input.openAPI(openAPI);
    input.config(codegen);

    final DefaultGenerator generator = new DefaultGenerator();
    List<File> files = generator.opts(input).generate();

    return files.stream().collect(Collectors.toMap(e -> e.getName().replace(outputPath, ""), i -> i));
  }

  /*
   * UPDATE: the following test has been ignored due to https://github.com/OpenAPITools/openapi-generator/pull/11081/
   * We will contact the contributor of the following test to see if the fix will break their use cases and
   * how we can fix it accordingly.
   */
  @Test
  @Ignore
  public void testMultipartCloud() throws IOException {
    final SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setLibrary("spring-cloud");
    codegen.setDelegatePattern(true);

    final Map<String, File> files = generateFiles(codegen, "src/test/resources/spring.av/spring/3_0/form-multipart-binary-array.yaml");

    // Check that the delegate handles the array and the file
    final File multipartApiDelegate = files.get("MultipartApiDelegate.java");
    assertFileContains(multipartApiDelegate.toPath(),
        "List<MultipartFile> files",
        "MultipartFile file");

    // Check that the api handles the array and the file
    final File multipartApi = files.get("Multipart10Api.java");
    assertFileContains(multipartApi.toPath(),
        "List<MultipartFile> files",
        "MultipartFile file");
  }

  @Test
  public void testRequestMappingAnnotation() throws IOException {
    final SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setLibrary("spring-boot");
    codegen.additionalProperties().put(REQUEST_MAPPING_OPTION, SpringCodegen.RequestMappingMode.api_interface);

    final Map<String, File> files = generateFiles(codegen, "src/test/resources/spring.av/spring/2_0/petstore.yaml");

    // Check that the @RequestMapping annotation is generated in the Api file
    final File petApiFile = files.get("Petv10Api.java");
    assertFileContains(petApiFile.toPath(), "@RequestMapping(\"${openapi.openAPIPetstore.base-path:/v2}\")");

    // Check that the @RequestMapping annotation is not generated in the Controller file
    final File petApiControllerFile = files.get("Petv10ApiController.java");
    assertFileNotContains(petApiControllerFile.toPath(), "@RequestMapping(\"${openapi.openAPIPetstore.base-path:/v2}\")");
  }

  @Test
  public void testNoRequestMappingAnnotation_spring_cloud_default() throws IOException {
    final SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setLibrary("spring-cloud");

    final Map<String, File> files = generateFiles(codegen, "src/test/resources/spring.av/spring/2_0/petstore.yaml");

    // Check that the @RequestMapping annotation is not generated in the Api file
    final File petApiFile = files.get("PetApi.java");
    JavaFileAssert.assertThat(petApiFile).assertTypeAnnotations().hasSize(3).containsWithName("Validated")
        .containsWithName("Generated").containsWithName("Tag");
  }

  @Test
  public void testNoRequestMappingAnnotation() throws IOException {
    final SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setLibrary("spring-cloud");
    codegen.additionalProperties().put(INTERFACE_ONLY, "true");
    codegen.additionalProperties().put(REQUEST_MAPPING_OPTION, SpringCodegen.RequestMappingMode.none);

    final Map<String, File> files = generateFiles(codegen, "src/test/resources/spring.av/spring/2_0/petstore.yaml");

    // Check that the @RequestMapping annotation is not generated in the Api file
    final File petApiFile = files.get("PetApi.java");
    JavaFileAssert.assertThat(petApiFile).assertTypeAnnotations().hasSize(3).containsWithName("Validated")
        .containsWithName("Generated").containsWithName("Tag");
  }

  @Test
  public void testSettersForConfigValues() throws Exception {
    final SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setHideGenerationTimestamp(true);
    codegen.setModelPackage("xx.yyyyyyyy.model");
    codegen.setApiPackage("xx.yyyyyyyy.api");
    codegen.setInvokerPackage("xx.yyyyyyyy.invoker");
    codegen.setBasePackage("xx.yyyyyyyy.base");
    codegen.setConfigPackage("xx.yyyyyyyy.config");
    codegen.setUnhandledException(true);
    codegen.processOpts();

    Assert.assertEquals(codegen.additionalProperties().get(CodegenConstants.HIDE_GENERATION_TIMESTAMP), Boolean.TRUE);
    Assert.assertEquals(codegen.isHideGenerationTimestamp(), true);
    Assert.assertEquals(codegen.modelPackage(), "xx.yyyyyyyy.model");
    Assert.assertEquals(codegen.additionalProperties().get(CodegenConstants.MODEL_PACKAGE), "xx.yyyyyyyy.model");
    Assert.assertEquals(codegen.apiPackage(), "xx.yyyyyyyy.api");
    Assert.assertEquals(codegen.additionalProperties().get(CodegenConstants.API_PACKAGE), "xx.yyyyyyyy.api");
    Assert.assertEquals(codegen.getInvokerPackage(), "xx.yyyyyyyy.invoker");
    Assert.assertEquals(codegen.additionalProperties().get(CodegenConstants.INVOKER_PACKAGE), "xx.yyyyyyyy.invoker");
    Assert.assertEquals(codegen.getBasePackage(), "xx.yyyyyyyy.base");
    Assert.assertEquals(codegen.additionalProperties().get(SpringCodegen.BASE_PACKAGE), "xx.yyyyyyyy.base");
    Assert.assertEquals(codegen.getConfigPackage(), "xx.yyyyyyyy.config");
    Assert.assertEquals(codegen.additionalProperties().get(SpringCodegen.CONFIG_PACKAGE), "xx.yyyyyyyy.config");
    Assert.assertEquals(codegen.isUnhandledException(), true);
    Assert.assertEquals(codegen.additionalProperties().get(SpringCodegen.UNHANDLED_EXCEPTION_HANDLING), true);
  }

  @Test
  public void useBeanValidationTruePerformBeanValidationFalseJava8TrueForFormatEmail() throws IOException {
    beanValidationForFormatEmail(true, false, true, "@javax.validation.constraints.Email", "@org.hibernate.validator.constraints.Email");
  }

  @Test
  public void useBeanValidationTruePerformBeanValidationTrueJava8FalseForFormatEmail() throws IOException {
    beanValidationForFormatEmail(true, true, false, "@javax.validation.constraints.Email", "@org.hibernate.validator.constraints.Email");
  }

  @Test
  public void useBeanValidationTruePerformBeanValidationFalseJava8TrueJakartaeeTrueForFormatEmail() throws IOException {
    beanValidationForFormatEmail(true, false, true, true, "@jakarta.validation.constraints.Email", "@javax.validation.constraints.Email");
  }

  // note: java8 option/mustache tag has been removed and default to true
  private void beanValidationForFormatEmail(boolean useBeanValidation, boolean performBeanValidation, boolean java8, String contains, String notContains) throws IOException {
    this.beanValidationForFormatEmail(useBeanValidation, performBeanValidation, java8, false, contains, notContains);
  }

  private void beanValidationForFormatEmail(boolean useBeanValidation, boolean performBeanValidation, boolean java8, boolean useJakarta, String contains, String notContains) throws IOException {
    File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();
    String outputPath = output.getAbsolutePath().replace('\\', '/');

    OpenAPI openAPI = new OpenAPIParser()
        .readLocation("src/test/resources/spring.av/spring/3_0/issue_4876_format_email.yaml", null, new ParseOptions()).getOpenAPI();

    SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setOutputDir(output.getAbsolutePath());
    codegen.setUseBeanValidation(useBeanValidation);
    codegen.setPerformBeanValidation(performBeanValidation);
    codegen.setUseSpringBoot3(useJakarta);

    ClientOptInput input = new ClientOptInput();
    input.openAPI(openAPI);
    input.config(codegen);

    DefaultGenerator generator = new DefaultGenerator();

    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");

    Map<String, File> files = generator.opts(input).generate().stream()
        .collect(Collectors.toMap(File::getName, Function.identity()));

    JavaFileAssert javaFileAssert = JavaFileAssert.assertThat(files.get("PersonWithEmailv10.java"));
    if (useBeanValidation) javaFileAssert.hasImports((useJakarta ? "jakarta" : "javax") + ".validation.constraints");
    if (performBeanValidation) javaFileAssert.hasImports("org.hibernate.validator.constraints");
    assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/model/PersonWithEmailv10.java"), contains);
    assertFileNotContains(Paths.get(outputPath + "/src/main/java/org/openapitools/model/PersonWithEmailv10.java"), notContains);
  }

  @Test
  public void useBeanValidationTruePerformBeanValidationTrueJava8TrueForFormatEmail() throws IOException {
    beanValidationForFormatEmail(true, true, true, "@javax.validation.constraints.Email", "@org.hibernate.validator.constraints.Email");
  }

  @Test
  public void reactiveMapTypeRequestMonoTest() throws IOException {
    File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();
    String outputPath = output.getAbsolutePath().replace('\\', '/');

    final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/spring.av/spring/3_0/issue_8045.yaml");
    final SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setOpenAPI(openAPI);
    codegen.setOutputDir(output.getAbsolutePath());

    codegen.additionalProperties().put(SpringCodegen.DELEGATE_PATTERN, "true");
    codegen.additionalProperties().put(SpringCodegen.REACTIVE, "true");

    ClientOptInput input = new ClientOptInput();
    input.openAPI(openAPI);
    input.config(codegen);

    DefaultGenerator generator = new DefaultGenerator();

    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");

    generator.opts(input).generate();

    assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/api/Somev10Api.java"), "Mono<Map<String, DummyRequestv10>>");
    assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/api/Somev10ApiDelegate.java"),
        "Mono<Map<String, DummyRequestv10>>");
    assertFileNotContains(Paths.get(outputPath + "/src/main/java/org/openapitools/api/Somev10Api.java"), "Mono<DummyRequestv10>");
    assertFileNotContains(Paths.get(outputPath + "/src/main/java/org/openapitools/api/Somev10ApiDelegate.java"), "Mono<DummyRequestv10>");
  }

  @Test
  public void shouldGenerateValidCodeForReactiveControllerWithoutParams_issue14907() throws IOException {
    File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();

    final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/spring.av/spring/3_0/bugs/issue_14907.yaml");
    final SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setOpenAPI(openAPI);
    codegen.setOutputDir(output.getAbsolutePath());

    codegen.additionalProperties().put(SpringCodegen.REACTIVE, "true");
    codegen.additionalProperties().put(USE_TAGS, "true");
    codegen.additionalProperties().put(SpringCodegen.DATE_LIBRARY, "java8");
    codegen.additionalProperties().put(INTERFACE_ONLY, "true");
    codegen.additionalProperties().put(SKIP_DEFAULT_INTERFACE, "true");
    codegen.additionalProperties().put(IMPLICIT_HEADERS, "true");
    codegen.additionalProperties().put(OPENAPI_NULLABLE, "false");

    ClientOptInput input = new ClientOptInput();
    input.openAPI(openAPI);
    input.config(codegen);

    DefaultGenerator generator = new DefaultGenerator();

    Map<String, File> files = generator.opts(input).generate().stream()
        .collect(Collectors.toMap(File::getName, Function.identity()));

    JavaFileAssert.assertThat(files.get("ConsentControllerApi.java"))
        .assertMethod("readAgreementsv10", "ServerWebExchange");
  }

  @Test
  public void shouldGenerateValidCodeWithPaginated_reactive_issue15265() throws IOException {
    File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();

    final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/spring.av/spring/3_0/bugs/issue_15265.yaml");
    final SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setOpenAPI(openAPI);
    codegen.setOutputDir(output.getAbsolutePath());

    codegen.additionalProperties().put(SpringCodegen.REACTIVE, "true");
    codegen.additionalProperties().put(USE_TAGS, "true");
    codegen.additionalProperties().put(SpringCodegen.DATE_LIBRARY, "java8");
    codegen.additionalProperties().put(INTERFACE_ONLY, "true");
    codegen.additionalProperties().put(SKIP_DEFAULT_INTERFACE, "true");
    codegen.additionalProperties().put(IMPLICIT_HEADERS, "true");
    codegen.additionalProperties().put(OPENAPI_NULLABLE, "false");

    ClientOptInput input = new ClientOptInput();
    input.openAPI(openAPI);
    input.config(codegen);

    DefaultGenerator generator = new DefaultGenerator();

    Map<String, File> files = generator.opts(input).generate().stream()
        .collect(Collectors.toMap(File::getName, Function.identity()));

    JavaFileAssert.assertThat(files.get("ConsentControllerApi.java"))
        .assertMethod("paginatedv10", "ServerWebExchange", "Pageable")
        .toFileAssert()
        .assertMethod("paginatedWithParamsv10", "String", "ServerWebExchange", "Pageable");
  }

  @Test
  public void shouldGenerateValidCodeWithPaginated_nonReactive_issue15265() throws IOException {
    File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();

    final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/spring.av/spring/3_0/bugs/issue_15265.yaml");
    final SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setOpenAPI(openAPI);
    codegen.setOutputDir(output.getAbsolutePath());

    codegen.additionalProperties().put(USE_TAGS, "true");
    codegen.additionalProperties().put(SpringCodegen.DATE_LIBRARY, "java8");
    codegen.additionalProperties().put(INTERFACE_ONLY, "true");
    codegen.additionalProperties().put(SKIP_DEFAULT_INTERFACE, "true");
    codegen.additionalProperties().put(IMPLICIT_HEADERS, "true");
    codegen.additionalProperties().put(OPENAPI_NULLABLE, "false");

    ClientOptInput input = new ClientOptInput();
    input.openAPI(openAPI);
    input.config(codegen);

    DefaultGenerator generator = new DefaultGenerator();

    Map<String, File> files = generator.opts(input).generate().stream()
        .collect(Collectors.toMap(File::getName, Function.identity()));

    JavaFileAssert.assertThat(files.get("ConsentControllerApi.java"))
        .assertMethod("paginatedv10", "Pageable")
        .toFileAssert()
        .assertMethod("paginatedWithParamsv10", "String", "Pageable");
  }

  @Test
  public void shouldEscapeReservedKeyWordsForRequestParameters_7506_Regression() throws Exception {
    final SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setLibrary("spring-boot");
    codegen.setDelegatePattern(true);

    final Map<String, File> files = generateFiles(codegen, "src/test/resources/spring.av/spring/3_0/issue7506.yaml");

    final File multipartArrayApiDelegate = files.get("Examplev10Api.java");
    assertFileContains(multipartArrayApiDelegate.toPath(), "@RequestPart(value = \"super\", required = false) MultipartFile _super");
    assertFileContains(multipartArrayApiDelegate.toPath(), "@RequestPart(value = \"package\", required = false) MultipartFile _package");
  }

  @Test
  public void doGeneratePathVariableForSimpleParam() throws IOException {
    File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();
    String outputPath = output.getAbsolutePath().replace('\\', '/');

    OpenAPI openAPI = new OpenAPIParser()
        .readLocation("src/test/resources/spring.av/spring/3_0/issue_6762.yaml", null, new ParseOptions()).getOpenAPI();

    SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setOutputDir(output.getAbsolutePath());
    codegen.additionalProperties().put(CXFServerFeatures.LOAD_TEST_DATA_FROM_FILE, "true");
    codegen.additionalProperties().put(DOCUMENTATION_PROVIDER, "springfox");

    ClientOptInput input = new ClientOptInput();
    input.openAPI(openAPI);
    input.config(codegen);

    DefaultGenerator generator = new DefaultGenerator();

    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");

    generator.opts(input).generate();

    assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/api/Zebrasv10Api.java"), "allowableValues = \"0, 1\"");
    assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/api/Zebrasv10Api.java"), "@PathVariable");
    assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/api/Bearsv10Api.java"),
        "allowableValues = \"sleeping, awake\"");
    assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/api/Bearsv10Api.java"), "@PathVariable");
    assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/api/Camelsv10Api.java"),
        "allowableValues = \"sleeping, awake\"");
    assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/api/Camelsv10Api.java"), "@PathVariable");
    assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/api/Giraffesv10Api.java"), "allowableValues = \"0, 1\"");
    assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/api/Giraffesv10Api.java"), "@PathVariable");
  }

  @Test
  public void shouldGenerateDefaultValueForEnumRequestParameter() throws IOException {
    File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();
    String outputPath = output.getAbsolutePath().replace('\\', '/');

    final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/spring.av/spring/3_0/issue_10278.yaml");
    final SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setOpenAPI(openAPI);
    codegen.setOutputDir(output.getAbsolutePath());

    codegen.additionalProperties().put(SpringCodegen.DELEGATE_PATTERN, "true");
    codegen.additionalProperties().put(SpringCodegen.REACTIVE, "true");

    ClientOptInput input = new ClientOptInput();
    input.openAPI(openAPI);
    input.config(codegen);

    DefaultGenerator generator = new DefaultGenerator();

    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");

    generator.opts(input).generate();

    assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/api/Getv10Api.java"),
        "@RequestParam(value = \"testParameter1\", required = false, defaultValue = \"BAR\")",
        "@RequestParam(value = \"TestParameter2\", required = false, defaultValue = \"BAR\")");
  }

  /**
   * test whether OpenAPIDocumentationConfig.java is generated fix issue #10287
   */
  @Test
  public void testConfigFileGeneration_springfox() {
    testConfigFileCommon(SPRINGFOX, SPRINGFOX_DESTINATIONFILE, SPRINGFOX_TEMPLATEFILE);
  }

  /**
   * test whether SpringDocDocumentationConfig.java is generated fix issue #12220
   */
  @Test
  public void testConfigFileGeneration_springdoc() {
    testConfigFileCommon(SPRINGDOC, SPRINGDOC_DESTINATIONFILE, SPRINGDOC_TEMPLATEFILE);
  }

  private void testConfigFileCommon(String documentationProvider, String destinationFile, String templateFileName) {
    final SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.additionalProperties().put(DOCUMENTATION_PROVIDER, documentationProvider);
    codegen.additionalProperties().put(SpringCodegen.INTERFACE_ONLY, false);
    codegen.additionalProperties().put(SpringCodegen.SPRING_CLOUD_LIBRARY, "spring-cloud");
    codegen.additionalProperties().put(SpringCodegen.REACTIVE, false);
    codegen.additionalProperties().put(SpringCodegen.API_FIRST, false);

    codegen.processOpts();

    final List<SupportingFile> supList = codegen.supportingFiles();
    String tmpFile;
    String desFile;
    boolean flag = false;
    for (final SupportingFile s : supList) {
      tmpFile = s.getTemplateFile();
      desFile = s.getDestinationFilename();

      if (templateFileName.equals(tmpFile)) {
        flag = true;
        assertEquals(desFile, destinationFile);
      }
    }
    if (!flag) {
      fail(templateFileName + " not generated");
    }
  }

  @Test
  public void shouldAddNotNullOnRequiredAttributes() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/spring/3_0/issue_5026-b.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();

    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");

    generator.opts(configurationPair.getKey()).generate();

    assertFileContains(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Dummyv10.java"), "status");
    assertFileContains(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Dummyv10.java"), "@NotNull");
    Files.readAllLines(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Dummyv10.java")).forEach(System.out::println);
  }

  @Test
  public void shouldNotAddNotNullOnReadOnlyAttributes() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/spring/3_0/issue_5026.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();

    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");

    generator.opts(configurationPair.getKey()).generate();

    assertFileContains(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Dummyv10.java"), "status");
    assertFileNotContains(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Dummyv10.java"), "@NotNull");
    Files.readAllLines(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Dummyv10.java")).forEach(System.out::println);
  }

  @Test
  public void testOneOf5381() throws IOException {
    File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();
    String outputPath = output.getAbsolutePath().replace('\\', '/');
    OpenAPI openAPI = new OpenAPIParser()
        .readLocation("src/test/resources/spring.av/spring/3_0/issue_5381.yaml", null, new ParseOptions()).getOpenAPI();

    SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setOutputDir(output.getAbsolutePath());
    codegen.additionalProperties().put(CXFServerFeatures.LOAD_TEST_DATA_FROM_FILE, "true");
    codegen.setUseOneOfInterfaces(true);

    ClientOptInput input = new ClientOptInput();
    input.openAPI(openAPI);
    input.config(codegen);

    DefaultGenerator generator = new DefaultGenerator();
    codegen.setHateoas(true);
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    //generator.setGeneratorPropertyDefault(CodegenConstants.USE_ONEOF_DISCRIMINATOR_LOOKUP, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.LEGACY_DISCRIMINATOR_BEHAVIOR, "false");

    codegen.setUseOneOfInterfaces(true);
    codegen.setLegacyDiscriminatorBehavior(false);

    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");

    generator.opts(input).generate();

    assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/model/Foov01.java"),
        "public class Foov01 implements FooRefOrValuev01");
    assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/model/FooRefv01.java"),
        "public class FooRefv01 implements FooRefOrValuev01");
    assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/model/FooRefOrValuev01.java"),
        "public interface FooRefOrValuev01");
  }

  @Test
  public void testOneOfAndAllOf() throws IOException {
    File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();
    String outputPath = output.getAbsolutePath().replace('\\', '/');
    OpenAPI openAPI = new OpenAPIParser()
        .readLocation("src/test/resources/spring.av/spring/3_0/oneof_polymorphism_and_inheritance.yaml", null, new ParseOptions()).getOpenAPI();

    SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setOutputDir(output.getAbsolutePath());
    codegen.additionalProperties().put(CXFServerFeatures.LOAD_TEST_DATA_FROM_FILE, "true");
    codegen.setUseOneOfInterfaces(true);

    ClientOptInput input = new ClientOptInput();
    input.openAPI(openAPI);
    input.config(codegen);

    DefaultGenerator generator = new DefaultGenerator();
    codegen.setHateoas(true);
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    //generator.setGeneratorPropertyDefault(CodegenConstants.USE_ONEOF_DISCRIMINATOR_LOOKUP, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.LEGACY_DISCRIMINATOR_BEHAVIOR, "false");

    codegen.setUseOneOfInterfaces(true);
    codegen.setLegacyDiscriminatorBehavior(false);

    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");

    generator.opts(input).generate();

    assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/model/Foov01.java"),
        "public class Foov01 extends Entityv01 implements FooRefOrValuev01");
    assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/model/FooRefv01.java"),
        "public class FooRefv01 extends EntityRefv01 implements FooRefOrValuev01");
    assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/model/FooRefOrValuev01.java"),
        "public interface FooRefOrValuev01");
    // previous bugs
    assertFileNotContains(Paths.get(outputPath + "/src/main/java/org/openapitools/model/BarRefv01.java"), "atTypesuper.hashCode");
    assertFileNotContains(Paths.get(outputPath + "/src/main/java/org/openapitools/model/BarRefv01.java"), "private String atBaseType");
    // imports for inherited properties
    assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/model/PizzaSpezialev01.java"), "import java.math.BigDecimal");
  }

  @Test
  public void testDiscriminatorWithMappingIssue14731() throws IOException {
    File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();
    String outputPath = output.getAbsolutePath().replace('\\', '/');
    OpenAPI openAPI = new OpenAPIParser()
        .readLocation("src/test/resources/spring.av/spring/3_0/bugs/issue_14731.yaml", null, new ParseOptions()).getOpenAPI();

    SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setOutputDir(output.getAbsolutePath());
    codegen.additionalProperties().put(CXFServerFeatures.LOAD_TEST_DATA_FROM_FILE, "true");
    codegen.setUseOneOfInterfaces(true);

    ClientOptInput input = new ClientOptInput();
    input.openAPI(openAPI);
    input.config(codegen);

    DefaultGenerator generator = new DefaultGenerator();
    codegen.setHateoas(true);
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.LEGACY_DISCRIMINATOR_BEHAVIOR, "false");

    codegen.setUseOneOfInterfaces(true);
    codegen.setLegacyDiscriminatorBehavior(false);
    codegen.setUseSpringBoot3(true);
    codegen.setModelNameSuffix("DTO");

    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");

    generator.opts(input).generate();

    assertFileNotContains(Paths.get(outputPath + "/src/main/java/org/openapitools/model/ChildWithMappingAv10DTO.java"), "@JsonTypeName");
    assertFileNotContains(Paths.get(outputPath + "/src/main/java/org/openapitools/model/ChildWithMappingBv10DTO.java"), "@JsonTypeName");
  }

  @Test
  public void testDiscriminatorWithoutMappingIssue14731() throws IOException {
    File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();
    String outputPath = output.getAbsolutePath().replace('\\', '/');
    OpenAPI openAPI = new OpenAPIParser()
        .readLocation("src/test/resources/spring.av/spring/3_0/bugs/issue_14731.yaml", null, new ParseOptions()).getOpenAPI();

    SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setOutputDir(output.getAbsolutePath());
    codegen.additionalProperties().put(CXFServerFeatures.LOAD_TEST_DATA_FROM_FILE, "true");
    codegen.setUseOneOfInterfaces(true);

    ClientOptInput input = new ClientOptInput();
    input.openAPI(openAPI);
    input.config(codegen);

    DefaultGenerator generator = new DefaultGenerator();
    codegen.setHateoas(true);
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.LEGACY_DISCRIMINATOR_BEHAVIOR, "false");

    codegen.setUseOneOfInterfaces(true);
    codegen.setLegacyDiscriminatorBehavior(false);
    codegen.setUseSpringBoot3(true);
    codegen.setModelNameSuffix("DTO");

    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");

    generator.opts(input).generate();

    assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/model/ChildWithoutMappingAv10DTO.java"), "@JsonTypeName");
    assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/model/ChildWithoutMappingBv10DTO.java"), "@JsonTypeName");
  }

  @Test
  public void testTypeMappings() {
    final SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.processOpts();
    Assert.assertEquals(codegen.typeMapping().get("file"), "org.springframework.core.io.Resource");
  }

  @Test
  public void testImportMappings() {
    final SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.processOpts();
    Assert.assertEquals(codegen.importMapping().get("org.springframework.core.io.Resource"), "org.springframework.core.io.Resource");
    Assert.assertEquals(codegen.importMapping().get("Pageable"), "org.springframework.data.domain.Pageable");
    Assert.assertEquals(codegen.importMapping().get("DateTimeFormat"), "org.springframework.format.annotation.DateTimeFormat");
    Assert.assertEquals(codegen.importMapping().get("ApiIgnore"), "springfox.documentation.annotations.ApiIgnore");
    Assert.assertEquals(codegen.importMapping().get("ParameterObject"), "org.springdoc.api.annotations.ParameterObject");
  }

  @Test(dataProvider = "issue11464TestCases")
  public void shouldGenerateOneTagAttributeForMultipleTags_Regression11464(String documentProvider, Consumer<String> assertFunction)
      throws IOException {
    File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();
    String outputPath = output.getAbsolutePath().replace('\\', '/');

    OpenAPI openAPI = new OpenAPIParser()
        .readLocation("src/test/resources/spring.av/spring/3_0/bugs/issue_11464.yaml", null, new ParseOptions()).getOpenAPI();

    SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setOutputDir(output.getAbsolutePath());
    codegen.additionalProperties().put(DOCUMENTATION_PROVIDER, documentProvider);
    ClientOptInput input = new ClientOptInput();
    input.openAPI(openAPI);
    input.config(codegen);

    DefaultGenerator generator = new DefaultGenerator();

    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");

    generator.opts(input).generate();

    assertFunction.accept(outputPath);
  }

  @DataProvider
  public Object[][] issue11464TestCases() {
    return new Object[][]{
        {DocumentationProviderFeatures.DocumentationProvider.SPRINGDOC.name(), (Consumer<String>) outputPath -> {
          assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/api/Nonev10Api.java"),
              "@Operation( operationId = \"getNonev10\", summary = \"No Tag\", responses = {");
          assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/api/Singlev10Api.java"),
              "@Operation( operationId = \"getSingleTagv10\", summary = \"Single Tag\", tags = { \"tag1\" }, responses = {");
          assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/api/Multiplev10Api.java"),
              "@Operation( operationId = \"getMultipleTagsv10\", summary = \"Multiple Tags\", tags = { \"tag1\", \"tag2\" }, responses = {");
        }},
        {DocumentationProviderFeatures.DocumentationProvider.SPRINGFOX.name(), (Consumer<String>) outputPath -> {
          assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/api/Nonev10Api.java"),
              "@ApiOperation( value = \"No Tag\", nickname = \"getNonev10\", notes = \"\", response = ");
          assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/api/Singlev10Api.java"),
              "@ApiOperation( tags = { \"tag1\" }, value = \"Single Tag\", nickname = \"getSingleTagv10\", notes = \"\", response = ");
          assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/api/Multiplev10Api.java"),
              "@ApiOperation( tags = { \"tag1\", \"tag2\" }, value = \"Multiple Tags\", nickname = \"getMultipleTagsv10\", notes = \"\", response = ");
        }},
    };
  }

  @Test
  public void apiFirstShouldNotGenerateApiOrModel() {
    final SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.additionalProperties().put(SpringCodegen.API_FIRST, true);
    codegen.processOpts();
    Assert.assertTrue(codegen.modelTemplateFiles().isEmpty());
    Assert.assertTrue(codegen.apiTemplateFiles().isEmpty());
  }

  @Test
  public void testIssue11323() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/spring/3_0/issue_11323.yml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();

    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");
    generator.opts(configurationPair.getKey()).generate();

    assertFileContains(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Addressv10.java"),
        "@JsonValue", "import com.fasterxml.jackson.annotation.JsonValue;");
  }

  @Test
  public void shouldPurAdditionalModelTypesOverAllModels() throws IOException {
    File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();
    String outputPath = output.getAbsolutePath().replace('\\', '/');

    OpenAPI openAPI = new OpenAPIParser()
        .readLocation("src/test/resources/spring.av/spring/3_0/petstore.yaml", null, new ParseOptions()).getOpenAPI();

    SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setOutputDir(output.getAbsolutePath());
    codegen.additionalProperties().put(SpringCodegen.ADDITIONAL_MODEL_TYPE_ANNOTATIONS,
        "@path.Annotation(param1 = \"test1\", param2 = 3);@path.Annotation2;@custom.Annotation");

    ClientOptInput input = new ClientOptInput();
    input.openAPI(openAPI);
    input.config(codegen);

    DefaultGenerator generator = new DefaultGenerator();

    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");
    generator.opts(input).generate();

    File[] generatedModels = new File(outputPath + "/src/main/java/org/openapitools/model").listFiles();
    assertThat(generatedModels).isNotEmpty();

    for (File modelPath : generatedModels) {
      JavaFileAssert.assertThat(modelPath)
          .assertTypeAnnotations()
          .containsWithName("custom.Annotation")
          .containsWithName("path.Annotation2")
          .containsWithNameAndAttributes("path.Annotation", ImmutableMap.of("param1", "\"test1\"", "param2", "3"));
    }
  }

  @Test
  public void shouldGenerateExternalDocs() throws IOException {
    File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();

    OpenAPI openAPI = new OpenAPIParser()
        .readLocation("src/test/resources/spring.av/spring/3_0/petstore.yaml", null, new ParseOptions()).getOpenAPI();
    SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setLibrary(SPRING_BOOT);
    codegen.setOutputDir(output.getAbsolutePath());
    codegen.additionalProperties().put(SpringCodegen.USE_TAGS, "true");
    codegen.additionalProperties().put(BeanValidationFeatures.USE_BEANVALIDATION, "true");

    ClientOptInput input = new ClientOptInput()
        .openAPI(openAPI)
        .config(codegen);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");

    Map<String, File> files = generator.opts(input).generate().stream()
        .collect(Collectors.toMap(File::getName, Function.identity()));

    JavaFileAssert.assertThat(files.get("PetApi.java"))
        .printFileContent()
        .hasImports("io.swagger.v3.oas.annotations.ExternalDocumentation")
        .assertMethod("updatePetv10")
        .assertMethodAnnotations()
        .containsWithName("Operation")
        .containsWithNameAndAttributes("Operation",
            ImmutableMap.of(
                "operationId", "\"updatePetv10\"",
                //"security", "{ @SecurityRequirement(name = \"petstore_auth\", scopes = { \"write:pets\", \"read:pets\" }) }",
                "externalDocs",
                "@ExternalDocumentation(description = \"API documentation for the updatePet operation\", url = \"http://petstore.swagger.io/v2/doc/updatePet\")"
            )
        );
  }

  @Test
  public void testHandleDefaultValue_issue8535() throws Exception {
    Map<String, Object> additionalProperties = new HashMap<>();
    additionalProperties.put(CXFServerFeatures.LOAD_TEST_DATA_FROM_FILE, "true");
    Map<String, File> files = generateFromContract("src/test/resources/spring.av/spring/3_0/issue_8535.yaml", SPRING_BOOT, additionalProperties);

    JavaFileAssert.assertThat(files.get("TestHeadersv10Api.java"))
        .assertMethod("headersTestv10")
        .hasParameter("headerNumber").withType("BigDecimal")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("RequestHeader", ImmutableMap.of("defaultValue", "\"11.2\""))
        .toParameter().toMethod()
        .hasParameter("headerString").withType("String")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("RequestHeader", ImmutableMap.of("defaultValue", "\"qwerty\""))
        .toParameter().toMethod()
        .hasParameter("headerStringWrapped").withType("String")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("RequestHeader", ImmutableMap.of("defaultValue", "\"qwerty\""))
        .toParameter().toMethod()
        .hasParameter("headerStringQuotes").withType("String")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("RequestHeader", ImmutableMap.of("defaultValue", "\"qwerty\\\"with quotes\\\" test\""))
        .toParameter().toMethod()
        .hasParameter("headerStringQuotesWrapped").withType("String")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("RequestHeader", ImmutableMap.of("defaultValue", "\"qwerty\\\"with quotes\\\" test\""))
        .toParameter().toMethod()
        .hasParameter("headerBoolean").withType("Boolean")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("RequestHeader", ImmutableMap.of("defaultValue", "\"true\""));

    JavaFileAssert.assertThat(files.get("TestQueryParamsv10Api.java"))
        .assertMethod("queryParamsTestv10")
        .hasParameter("queryNumber").withType("BigDecimal")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("RequestParam", ImmutableMap.of("defaultValue", "\"11.2\""))
        .toParameter().toMethod()
        .hasParameter("queryString").withType("String")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("RequestParam", ImmutableMap.of("defaultValue", "\"qwerty\""))
        .toParameter().toMethod()
        .hasParameter("queryStringWrapped").withType("String")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("RequestParam", ImmutableMap.of("defaultValue", "\"qwerty\""))
        .toParameter().toMethod()
        .hasParameter("queryStringQuotes").withType("String")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("RequestParam", ImmutableMap.of("defaultValue", "\"qwerty\\\"with quotes\\\" test\""))
        .toParameter().toMethod()
        .hasParameter("queryStringQuotesWrapped").withType("String")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("RequestParam", ImmutableMap.of("defaultValue", "\"qwerty\\\"with quotes\\\" test\""))
        .toParameter().toMethod()
        .hasParameter("queryBoolean").withType("Boolean")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("RequestParam", ImmutableMap.of("defaultValue", "\"true\""));
  }

  @Test
  public void testExtraAnnotations() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/spring/3_0/issue_11772.yml", SPRING_BOOT, true);
    String baseOutputPath = configurationPair.getValue() + "/src/main/java/org/openapitools/model";

    DefaultGenerator generator = new DefaultGenerator();

    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");
    generator.opts(configurationPair.getKey()).generate();

    JavaFileAssert.assertThat(java.nio.file.Paths.get(baseOutputPath + "/EmployeeEntityv10.java"))
        .assertTypeAnnotations()
        .containsWithName("javax.persistence.Entity")
        .containsWithNameAndAttributes("javax.persistence.Table", ImmutableMap.of("name", "\"employees\""))
        .toType()
        .hasProperty("assignments")
        .assertPropertyAnnotations()
        .containsWithNameAndAttributes("javax.persistence.OneToMany", ImmutableMap.of("mappedBy", "\"employee\""))
        .toProperty()
        .toType();

    JavaFileAssert.assertThat(java.nio.file.Paths.get(baseOutputPath + "/Employeev10.java"))
        .assertTypeAnnotations()
        .containsWithName("javax.persistence.MappedSuperclass")
        .toType()
        .hasProperty("id")
        .assertPropertyAnnotations()
        .containsWithName("javax.persistence.Id")
        .toProperty()
        .toType()
        .hasProperty("email")
        .assertPropertyAnnotations()
        .containsWithName("org.hibernate.annotations.Formula")
        .toProperty()
        .toType()
        .hasProperty("hasAcceptedTerms")
        .assertPropertyAnnotations()
        .containsWithName("javax.persistence.Transient")
        .toProperty()
        .toType();

    JavaFileAssert.assertThat(java.nio.file.Paths.get(baseOutputPath + "/SurveyGroupEntityv10.java"))
        .assertTypeAnnotations()
        .containsWithName("javax.persistence.Entity")
        .containsWithNameAndAttributes("javax.persistence.Table", ImmutableMap.of("name", "\"survey_groups\""))
        .toType()
        .hasProperty("assignments")
        .assertPropertyAnnotations()
        .containsWithName("javax.persistence.OneToMany")
        .containsWithNameAndAttributes("javax.persistence.JoinColumn", ImmutableMap.of("name", "\"survey_group_id\""))
        .toProperty()
        .toType()
        .hasProperty("disabled")
        .assertPropertyAnnotations()
        .containsWithNameAndAttributes("javax.persistence.Column", ImmutableMap.of("nullable", "false"))
        .toProperty()
        .toType();

    JavaFileAssert.assertThat(java.nio.file.Paths.get(baseOutputPath + "/SurveyGroupv10.java"))
        .assertTypeAnnotations()
        .containsWithName("javax.persistence.MappedSuperclass")
        .containsWithName("javax.persistence.EntityListeners")
        .toType()
        .hasProperty("id")
        .assertPropertyAnnotations()
        .containsWithName("javax.persistence.Id")
        .containsWithNameAndAttributes("javax.persistence.GeneratedValue", ImmutableMap.of("generator", "\"UUID\""))
        .containsWithNameAndAttributes("org.hibernate.annotations.GenericGenerator", ImmutableMap.of("name", "\"UUID\"", "strategy", "\"org.hibernate.id.UUIDGenerator\""))
        .containsWithNameAndAttributes("javax.persistence.Column", ImmutableMap.of("name", "\"id\"", "updatable", "false", "nullable", "false"))
        .toProperty()
        .toType()
        .hasProperty("createdDate")
        .assertPropertyAnnotations()
        .containsWithName("org.springframework.data.annotation.CreatedDate")
        .toProperty()
        .toType()
        .hasProperty("createdBy")
        .assertPropertyAnnotations()
        .containsWithName("org.springframework.data.annotation.CreatedBy")
        .toProperty()
        .toType()
        .hasProperty("modifiedDate")
        .assertPropertyAnnotations()
        .containsWithName("org.springframework.data.annotation.LastModifiedDate")
        .toProperty()
        .toType()
        .hasProperty("modifiedBy")
        .assertPropertyAnnotations()
        .containsWithName("org.springframework.data.annotation.LastModifiedBy")
        .toProperty()
        .toType()
        .hasProperty("opportunityId")
        .assertPropertyAnnotations()
        .containsWithNameAndAttributes("javax.persistence.Column", ImmutableMap.of("unique", "true"))
        .toProperty()
        .toType()
        .hasProperty("submissionStatus")
        .assertPropertyAnnotations()
        .containsWithName("javax.persistence.Transient")
        .toProperty()
        .toType();
  }

  @Test
  public void testResponseWithArray_issue11897() throws Exception {
    Map<String, Object> additionalProperties = new HashMap<>();
    additionalProperties.put(AbstractJavaCodegen.FULL_JAVA_UTIL, "true");
    additionalProperties.put(SpringCodegen.USE_TAGS, "true");
    additionalProperties.put(SpringCodegen.INTERFACE_ONLY, "true");
    additionalProperties.put(SpringCodegen.SKIP_DEFAULT_INTERFACE, "true");
    additionalProperties.put(SpringCodegen.PERFORM_BEANVALIDATION, "true");
    additionalProperties.put(SpringCodegen.SPRING_CONTROLLER, "true");
    additionalProperties.put(CodegenConstants.SERIALIZATION_LIBRARY, "jackson");
    Map<String, File> files = generateFromContract("src/test/resources/spring.av/spring/3_0/bugs/issue_11897.yaml", SPRING_BOOT, additionalProperties);

    JavaFileAssert.assertThat(files.get("MetadataApi.java"))
        .assertMethod("getWithArrayOfObjectsv31").hasReturnType("ResponseEntity<List<TestResponsev31>>")
        .toFileAssert()
        .assertMethod("getWithArrayOfStringv31").hasReturnType("ResponseEntity<List<String>>")
        .toFileAssert()
        .assertMethod("getWithSetOfObjectsv31").hasReturnType("ResponseEntity<Set<TestResponsev31>>")
        .toFileAssert()
        .assertMethod("getWithSetOfStringsv31").hasReturnType("ResponseEntity<Set<String>>")
        .toFileAssert()
        .assertMethod("getWithMapOfObjectsv31").hasReturnType("ResponseEntity<Map<String, TestResponsev31>>")
        .toFileAssert()
        .assertMethod("getWithMapOfStringsv31").hasReturnType("ResponseEntity<Map<String, String>>");
  }

  @Test
  public void testResponseWithArray_issue12524() throws Exception {
    GlobalSettings.setProperty("skipFormModel", "true");

    try {
      Map<String, Object> additionalProperties = new HashMap<>();
      additionalProperties.put(DOCUMENTATION_PROVIDER, "none");
      additionalProperties.put(ANNOTATION_LIBRARY, "none");
      additionalProperties.put(RETURN_SUCCESS_CODE, "true");
      Map<String, File> files = generateFromContract("src/test/resources/spring.av/spring/3_0/bugs/issue_12524.json", SPRING_BOOT, additionalProperties);

      JavaFileAssert.assertThat(files.get("API01ListOfStuffv10.java"))
          .hasImports("com.fasterxml.jackson.annotation.JsonTypeName");
    } finally {
      GlobalSettings.reset();
    }
  }

  @Test
  public void paramObjectImportForDifferentSpringBootVersions_issue14077() throws Exception {
    Map<String, Object> additionalProperties = new HashMap<>();
    additionalProperties.put(SpringCodegen.USE_TAGS, "true");
    additionalProperties.put(DOCUMENTATION_PROVIDER, "springdoc");
    additionalProperties.put(SpringCodegen.INTERFACE_ONLY, "true");
    additionalProperties.put(SpringCodegen.SKIP_DEFAULT_INTERFACE, "true");
    Map<String, File> files = generateFromContract("src/test/resources/spring.av/spring/2_0/petstore-with-spring-pageable.yaml", SPRING_BOOT,
        additionalProperties);

    JavaFileAssert.assertThat(files.get("PetApi.java"))
        .hasImports("org.springdoc.api.annotations.ParameterObject")
        .assertMethod("findPetsByStatusv10")
        .hasParameter("pageable").withType("Pageable")
        .assertParameterAnnotations()
        .containsWithName("ParameterObject");

    // different import for SB3
    additionalProperties.put(USE_SPRING_BOOT3, "true");
    files = generateFromContract("src/test/resources/spring.av/spring/2_0/petstore-with-spring-pageable.yaml", SPRING_BOOT, additionalProperties);

    JavaFileAssert.assertThat(files.get("PetApi.java"))
        .hasImports("org.springdoc.core.annotations.ParameterObject")
        .assertMethod("findPetsByStatusv10")
        .hasParameter("pageable").withType("Pageable")
        .assertParameterAnnotations()
        .containsWithName("ParameterObject");
  }

  @Test
  public void shouldSetDefaultValueForMultipleArrayItems() throws IOException {
    Map<String, Object> additionalProperties = new HashMap<>();
    additionalProperties.put(AbstractJavaCodegen.FULL_JAVA_UTIL, "true");
    additionalProperties.put(SpringCodegen.USE_TAGS, "true");
    additionalProperties.put(SpringCodegen.INTERFACE_ONLY, "true");
    additionalProperties.put(SpringCodegen.SKIP_DEFAULT_INTERFACE, "true");
    additionalProperties.put(SpringCodegen.PERFORM_BEANVALIDATION, "true");
    additionalProperties.put(SpringCodegen.SPRING_CONTROLLER, "true");
    additionalProperties.put(CodegenConstants.SERIALIZATION_LIBRARY, "jackson");

    Map<String, File> files = generateFromContract("src/test/resources/spring.av/spring/3_0/bugs/issue_11957.yaml", SPRING_BOOT, additionalProperties);

    JavaFileAssert.assertThat(files.get("SearchApi.java"))
        .assertMethod("defaultListv10")
        .hasParameter("orderBy")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("RequestParam", ImmutableMap.of("defaultValue", "\"updatedAt:DESC,createdAt:DESC\""))
        .toParameter().toMethod().toFileAssert()
        .assertMethod("defaultSetv10")
        .hasParameter("orderBy")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("RequestParam", ImmutableMap.of("defaultValue", "\"updatedAt:DESC,createdAt:DESC\""))
        .toParameter().toMethod().toFileAssert()
        .assertMethod("emptyDefaultListv10")
        .hasParameter("orderBy")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("RequestParam", ImmutableMap.of("defaultValue", "\"\""))
        .toParameter().toMethod().toFileAssert()
        .assertMethod("emptyDefaultSetv10")
        .hasParameter("orderBy")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("RequestParam", ImmutableMap.of("defaultValue", "\"\""));
  }

  @Test
  public void testPutItemsMethodContainsKeyInSuperClassMethodCall_issue12494() throws IOException {
    Map<String, File> files = generateFromContract("src/test/resources/spring.av/spring/3_0/bugs/issue_12494.yaml", null);

    JavaFileAssert.assertThat(files.get("ChildClassv10.java"))
        .assertMethod("putSomeMapItem")
        .bodyContainsLines("super.putSomeMapItem(key, someMapItem);");
  }

  @Test
  public void shouldHandleCustomResponseType_issue11731() throws IOException {
    Map<String, File> files = generateFromContract("src/test/resources/spring.av/spring/3_0/bugs/issue_11731.yaml", SPRING_BOOT);

    JavaFileAssert.assertThat(files.get("Customersv10Api.java"))
        .assertMethod("getAllUsingGET1v10")
        .bodyContainsLines("if (mediaType.isCompatibleWith(MediaType.valueOf(\"application/hal+json\"))) {");
  }

  @Test
  public void shouldHandleContentTypeWithSecondWildcardSubtype_issue12457() throws IOException {
    Map<String, Object> additionalProperties = new HashMap<>();
    additionalProperties.put(SpringCodegen.USE_TAGS, "true");
    Map<String, File> files = generateFromContract("src/test/resources/spring.av/spring/3_0/bugs/issue_12457.yaml", SPRING_BOOT, additionalProperties);

    JavaFileAssert.assertThat(files.get("UsersApi.java"))
        .assertMethod("wildcardSubTypeForContentTypev10")
        .assertMethodAnnotations()
        .containsWithNameAndAttributes("RequestMapping", ImmutableMap.of(
            "produces", "{ \"application/json\", \"application/*\" }",
            "consumes", "{ \"application/octet-stream\", \"application/*\" }"
        ));
  }

  @Test
  public void shouldGenerateDiscriminatorFromAllOfWhenUsingLegacyDiscriminatorBehaviour_issue12692() throws IOException {
    Map<String, Object> additionalProperties = new HashMap<>();
    additionalProperties.put(CodegenConstants.LEGACY_DISCRIMINATOR_BEHAVIOR, "true");
    Map<String, File> output = generateFromContract("src/test/resources/spring.av/spring/3_0/bugs/issue_12692.yml", SPRING_BOOT, additionalProperties);

    String jsonTypeInfo = "@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = \"type\", visible = true)";
    String jsonSubType = "@JsonSubTypes({\n" +
        "  @JsonSubTypes.Type(value = Catv10.class, name = \"cat\")" +
        "})";
    assertFileContains(output.get("Petv10.java").toPath(), jsonTypeInfo, jsonSubType);
  }

  @Test
  public void shouldGenerateBeanValidationOnHeaderParams() throws IOException {
    File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();

    OpenAPI openAPI = new OpenAPIParser()
        .readLocation("src/test/resources/spring.av/spring/3_0/bugs/issue_7125.json", null, new ParseOptions()).getOpenAPI();
    SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setLibrary(SPRING_BOOT);
    codegen.setOutputDir(output.getAbsolutePath());
    codegen.additionalProperties().put(SpringCodegen.USE_TAGS, "true");
    codegen.additionalProperties().put(BeanValidationFeatures.USE_BEANVALIDATION, "true");

    ClientOptInput input = new ClientOptInput()
        .openAPI(openAPI)
        .config(codegen);

    DefaultGenerator generator = new DefaultGenerator();
    Map<String, File> files = generator.opts(input).generate().stream()
        .collect(Collectors.toMap(File::getName, Function.identity()));

    JavaFileAssert.assertThat(files.get("SomeMethodApi.java"))
        .printFileContent()
        .assertMethod("methodWithValidationv10")
        .hasParameter("headerOne")
        .assertParameterAnnotations()
        .containsWithName("RequestHeader")
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Size", ImmutableMap.of(
            "min", "1",
            "max", "10"
        ))
        .containsWithNameAndAttributes("Pattern", ImmutableMap.of("regexp", "\"\\\\d+\""))
        .toParameter()
        .toMethod()
        .hasParameter("headerTwo")
        .assertParameterAnnotations()
        .containsWithName("RequestHeader")
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Min", ImmutableMap.of("value", "500"))
        .containsWithNameAndAttributes("Max", ImmutableMap.of("value", "10000"));
  }

  @Test
  public void requiredFieldShouldIncludeNotNullAnnotation_issue13365() throws IOException {

    SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setLibrary(SPRING_BOOT);
    codegen.additionalProperties().put(SpringCodegen.INTERFACE_ONLY, "true");
    codegen.additionalProperties().put(SpringCodegen.USE_BEANVALIDATION, "false");
    codegen.additionalProperties().put(SpringCodegen.PERFORM_BEANVALIDATION, "false");
    codegen.additionalProperties().put(SpringCodegen.OPENAPI_NULLABLE, "false");
    codegen.additionalProperties().put(SpringCodegen.UNHANDLED_EXCEPTION_HANDLING, "false");
    codegen.additionalProperties().put(CodegenConstants.SORT_MODEL_PROPERTIES_BY_REQUIRED_FLAG, "false");
    codegen.additionalProperties().put(CodegenConstants.SORT_PARAMS_BY_REQUIRED_FLAG, "false");
    codegen.additionalProperties().put(CodegenConstants.SERIALIZATION_LIBRARY, "jackson");
    codegen.additionalProperties().put(CodegenConstants.ENUM_PROPERTY_NAMING, "PascalCase");
    codegen.additionalProperties().put(SpringCodegen.USE_TAGS, "true");

    DefaultGenerator generator = new DefaultGenerator();
    Map<String, File> files = generateFiles(codegen, "src/test/resources/spring.av/spring/3_0/bugs/issue_13365.yml");

    //Assert that NotNull annotation exists alone with no other BeanValidation annotations
    JavaFileAssert javaFileAssert = JavaFileAssert.assertThat(files.get("Personv10.java"))
        .printFileContent();
    javaFileAssert.assertMethod("getName").assertMethodAnnotations()
        .containsWithName("NotNull").anyMatch(annotation ->
            !annotation.getNameAsString().equals("Valid") ||
                !annotation.getNameAsString().equals("Pattern") ||
                !annotation.getNameAsString().equals("Email") ||
                !annotation.getNameAsString().equals("Size"));
    javaFileAssert.hasImports("javax.validation.constraints.NotNull");
  }

  @Test
  public void requiredFieldShouldIncludeNotNullAnnotationJakarta_issue13365_issue13885() throws IOException {

    SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setLibrary(SPRING_BOOT);
    codegen.additionalProperties().put(SpringCodegen.INTERFACE_ONLY, "true");
    codegen.additionalProperties().put(SpringCodegen.USE_BEANVALIDATION, "false");
    codegen.additionalProperties().put(SpringCodegen.PERFORM_BEANVALIDATION, "false");
    codegen.additionalProperties().put(SpringCodegen.USE_SPRING_BOOT3, "true");
    codegen.additionalProperties().put(SpringCodegen.OPENAPI_NULLABLE, "false");
    codegen.additionalProperties().put(SpringCodegen.UNHANDLED_EXCEPTION_HANDLING, "false");
    codegen.additionalProperties().put(CodegenConstants.SORT_MODEL_PROPERTIES_BY_REQUIRED_FLAG, "false");
    codegen.additionalProperties().put(CodegenConstants.SORT_PARAMS_BY_REQUIRED_FLAG, "false");
    codegen.additionalProperties().put(CodegenConstants.SERIALIZATION_LIBRARY, "jackson");
    codegen.additionalProperties().put(CodegenConstants.ENUM_PROPERTY_NAMING, "PascalCase");
    codegen.additionalProperties().put(SpringCodegen.USE_TAGS, "true");

    DefaultGenerator generator = new DefaultGenerator();
    Map<String, File> files = generateFiles(codegen, "src/test/resources/spring.av/spring/3_0/bugs/issue_13365.yml");

    //Assert that NotNull annotation exists alone with no other BeanValidation annotations
    JavaFileAssert javaFileAssert = JavaFileAssert.assertThat(files.get("Personv10.java"))
        .printFileContent();
    javaFileAssert.assertMethod("getName").assertMethodAnnotations()
        .containsWithName("NotNull").anyMatch(annotation ->
            !annotation.getNameAsString().equals("Valid") ||
                !annotation.getNameAsString().equals("Pattern") ||
                !annotation.getNameAsString().equals("Email") ||
                !annotation.getNameAsString().equals("Size"));
    javaFileAssert.hasImports("jakarta.validation.constraints.NotNull");
  }

  @Test
  public void nonRequiredFieldShouldNotIncludeNotNullAnnotation_issue13365() throws IOException {

    SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setLibrary(SPRING_BOOT);
    codegen.additionalProperties().put(SpringCodegen.INTERFACE_ONLY, "true");
    codegen.additionalProperties().put(SpringCodegen.USE_BEANVALIDATION, "true");
    codegen.additionalProperties().put(SpringCodegen.PERFORM_BEANVALIDATION, "true");
    codegen.additionalProperties().put(SpringCodegen.OPENAPI_NULLABLE, "false");
    codegen.additionalProperties().put(SpringCodegen.UNHANDLED_EXCEPTION_HANDLING, "false");
    codegen.additionalProperties().put(CodegenConstants.SORT_MODEL_PROPERTIES_BY_REQUIRED_FLAG, "false");
    codegen.additionalProperties().put(CodegenConstants.SORT_PARAMS_BY_REQUIRED_FLAG, "false");
    codegen.additionalProperties().put(CodegenConstants.SERIALIZATION_LIBRARY, "jackson");
    codegen.additionalProperties().put(CodegenConstants.ENUM_PROPERTY_NAMING, "PascalCase");
    codegen.additionalProperties().put(SpringCodegen.USE_TAGS, "true");

    Map<String, File> files = generateFiles(codegen, "src/test/resources/spring.av/spring/3_0/bugs/issue_13365.yml");

    JavaFileAssert javaFileAssert = JavaFileAssert.assertThat(files.get("Alienv10.java"))
        .printFileContent();
    javaFileAssert.assertMethod("getName")
        .assertMethodAnnotations().anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));
    javaFileAssert.hasNoImports("javax.validation.constraints.NotNull");
  }

  @Test
  public void requiredFieldShouldIncludeNotNullAnnotationWithBeanValidationTrue_issue14252() throws IOException {

    SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setLibrary(SPRING_BOOT);
    codegen.additionalProperties().put(CodegenConstants.SERIALIZE_BIG_DECIMAL_AS_STRING, "true");

    Map<String, File> files = generateFiles(codegen, "src/test/resources/spring.av/spring/3_0/bugs/issue_14252.yaml");

    JavaFileAssert.assertThat(files.get("MyResponsev10.java"))
        .printFileContent()
        .hasImports("com.fasterxml.jackson.databind.annotation.JsonSerialize", "com.fasterxml.jackson.databind.ser.std.ToStringSerializer")
        .assertMethod("getMyPropTypeNumber")
        .assertMethodAnnotations()
        .containsWithNameAndAttributes("JsonSerialize", ImmutableMap.of(
            "using", "ToStringSerializer.class"
        ));
  }

  @Test
  public void requiredFieldShouldIncludeNotNullAnnotationWithBeanValidationTrue_issue13365() throws IOException {

    SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setLibrary(SPRING_BOOT);
    codegen.additionalProperties().put(SpringCodegen.INTERFACE_ONLY, "true");
    codegen.additionalProperties().put(SpringCodegen.USE_BEANVALIDATION, "true");
    codegen.additionalProperties().put(SpringCodegen.PERFORM_BEANVALIDATION, "false");
    codegen.additionalProperties().put(SpringCodegen.OPENAPI_NULLABLE, "false");
    codegen.additionalProperties().put(SpringCodegen.UNHANDLED_EXCEPTION_HANDLING, "false");
    codegen.additionalProperties().put(CodegenConstants.SORT_MODEL_PROPERTIES_BY_REQUIRED_FLAG, "false");
    codegen.additionalProperties().put(CodegenConstants.SORT_PARAMS_BY_REQUIRED_FLAG, "false");
    codegen.additionalProperties().put(CodegenConstants.SERIALIZATION_LIBRARY, "jackson");
    codegen.additionalProperties().put(CodegenConstants.ENUM_PROPERTY_NAMING, "PascalCase");
    codegen.additionalProperties().put(SpringCodegen.USE_TAGS, "true");

    Map<String, File> files = generateFiles(codegen, "src/test/resources/spring.av/spring/3_0/bugs/issue_13365.yml");

    JavaFileAssert javaFileAssert = JavaFileAssert.assertThat(files.get("Personv10.java"))
        .printFileContent();
    javaFileAssert.assertMethod("getName").assertMethodAnnotations()
        .containsWithName("NotNull").containsWithName("Size").containsWithName("javax.validation.constraints.Email");
    javaFileAssert
        .hasNoImports("javax.validation.constraints.NotNull")
        .hasImports("javax.validation.constraints");
  }

  @Test
  public void shouldUseEqualsNullableForArrayWhenSetInConfig_issue13385() throws IOException {
    File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();

    OpenAPI openAPI = new OpenAPIParser()
        .readLocation("src/test/resources/spring.av/spring/3_0/bugs/issue_13385.yml", null, new ParseOptions()).getOpenAPI();
    SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setLibrary(SPRING_BOOT);
    codegen.setOutputDir(output.getAbsolutePath());
    codegen.additionalProperties().put(SpringCodegen.INTERFACE_ONLY, "true");
    codegen.additionalProperties().put(SpringCodegen.USE_BEANVALIDATION, "true");
    codegen.additionalProperties().put(SpringCodegen.PERFORM_BEANVALIDATION, "true");
    codegen.additionalProperties().put(CodegenConstants.MODEL_PACKAGE, "xyz.model");
    codegen.additionalProperties().put(CodegenConstants.API_PACKAGE, "xyz.controller");

    ClientOptInput input = new ClientOptInput()
        .openAPI(openAPI)
        .config(codegen);

    DefaultGenerator generator = new DefaultGenerator();
    Map<String, File> files = generator.opts(input).generate().stream()
        .collect(Collectors.toMap(File::getName, Function.identity()));

    JavaFileAssert.assertThat(files.get("TestObjectv10.java"))
        .printFileContent()
        .assertMethod("equals")
        .bodyContainsLines("return equalsNullable(this.picture, testObjectv10.picture);");
  }

  @Test
  public void shouldNotUseEqualsNullableForArrayWhenNotSetInConfig_issue13385() throws IOException {
    File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();

    OpenAPI openAPI = new OpenAPIParser()
        .readLocation("src/test/resources/spring.av/spring/3_0/bugs/issue_13385_2.yml", null, new ParseOptions()).getOpenAPI();
    SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setLibrary(SPRING_BOOT);
    codegen.setOutputDir(output.getAbsolutePath());
    codegen.additionalProperties().put(SpringCodegen.INTERFACE_ONLY, "true");
    codegen.additionalProperties().put(SpringCodegen.USE_BEANVALIDATION, "true");
    codegen.additionalProperties().put(SpringCodegen.PERFORM_BEANVALIDATION, "true");
    codegen.additionalProperties().put(CodegenConstants.MODEL_PACKAGE, "xyz.model");
    codegen.additionalProperties().put(CodegenConstants.API_PACKAGE, "xyz.controller");

    ClientOptInput input = new ClientOptInput()
        .openAPI(openAPI)
        .config(codegen);

    DefaultGenerator generator = new DefaultGenerator();
    Map<String, File> files = generator.opts(input).generate().stream()
        .collect(Collectors.toMap(File::getName, Function.identity()));

    JavaFileAssert.assertThat(files.get("TestObjectv10.java"))
        .printFileContent()
        .assertMethod("equals")
        .bodyContainsLines("return Arrays.equals(this.picture, testObjectv10.picture);");
  }

  @Test
  public void useBeanValidationGenerateAnnotationsForRequestBody_issue13932() throws IOException {
    File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();

    OpenAPI openAPI = new OpenAPIParser()
        .readLocation("src/test/resources/spring.av/spring/3_0/bugs/issue_13932.yml", null, new ParseOptions()).getOpenAPI();
    SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setLibrary(SPRING_BOOT);
    codegen.setOutputDir(output.getAbsolutePath());
    codegen.additionalProperties().put(SpringCodegen.INTERFACE_ONLY, "true");
    codegen.additionalProperties().put(SpringCodegen.USE_BEANVALIDATION, "true");
    codegen.additionalProperties().put(CodegenConstants.MODEL_PACKAGE, "xyz.model");
    codegen.additionalProperties().put(CodegenConstants.API_PACKAGE, "xyz.controller");

    ClientOptInput input = new ClientOptInput()
        .openAPI(openAPI)
        .config(codegen);

    DefaultGenerator generator = new DefaultGenerator();
    Map<String, File> files = generator.opts(input).generate().stream()
        .collect(Collectors.toMap(File::getName, Function.identity()));

    JavaFileAssert.assertThat(files.get("Addv10Api.java"))
        .printFileContent()
        .assertMethod("addv10Post")
        .hasParameter("body")
        .assertParameterAnnotations()
        .containsWithNameAndAttributes("Min", ImmutableMap.of("value", "2"));
  }

  @Test
  public void shouldHandleSeparatelyInterfaceAndModelAdditionalAnnotations() throws IOException {
    File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();

    OpenAPI openAPI = new OpenAPIParser()
        .readLocation("src/test/resources/spring.av/spring/3_0/bugs/issue_13917.yaml", null, new ParseOptions()).getOpenAPI();
    SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setLibrary(SPRING_BOOT);
    codegen.setOutputDir(output.getAbsolutePath());
    codegen.additionalProperties().put(SpringCodegen.INTERFACE_ONLY, "true");
    codegen.additionalProperties().put(SpringCodegen.USE_BEANVALIDATION, "true");
    codegen.additionalProperties().put(SpringCodegen.PERFORM_BEANVALIDATION, "true");
    codegen.additionalProperties().put(CodegenConstants.MODEL_PACKAGE, "xyz.model");
    codegen.additionalProperties().put(CodegenConstants.API_PACKAGE, "xyz.controller");
    codegen.additionalProperties()
        .put(AbstractJavaCodegen.ADDITIONAL_MODEL_TYPE_ANNOTATIONS, "@marker.Class1;@marker.Class2;@marker.Common");
    codegen.additionalProperties().put(AbstractJavaCodegen.ADDITIONAL_ONE_OF_TYPE_ANNOTATIONS, "@marker.Interface1;@marker.Common");

    ClientOptInput input = new ClientOptInput()
        .openAPI(openAPI)
        .config(codegen);

    DefaultGenerator generator = new DefaultGenerator();
    Map<String, File> files = generator.opts(input).generate().stream()
        .collect(Collectors.toMap(File::getName, Function.identity()));

    JavaFileAssert.assertThat(files.get("PatchRequestInnerv01.java"))
        .isInterface()
        .assertTypeAnnotations()
        .containsWithName("marker.Interface1")
        .containsWithName("marker.Common");

    JavaFileAssert.assertThat(files.get("JSONPatchRequestRemovev01.java"))
        .isNormalClass()
        .assertTypeAnnotations()
        .containsWithName("marker.Class1")
        .containsWithName("marker.Class2")
        .containsWithName("marker.Common");
  }

  @Test
  public void contractWithoutEnumDoesNotContainsEnumConverter() throws IOException {
    Map<String, File> output = generateFromContract("src/test/resources/spring.av/spring/3_0/generic.yaml", SPRING_BOOT);

    assertThat(output).doesNotContainKey("EnumConverterConfiguration.java");
  }

  @Test
  public void contractWithEnumContainsEnumConverter() throws IOException {
    Map<String, File> output = generateFromContract("src/test/resources/spring.av/spring/3_0/enum.yaml", SPRING_BOOT);

    JavaFileAssert.assertThat(output.get("EnumConverterConfiguration.java"))
        .assertMethod("typev10Converter");
  }

  @Test
  public void shouldUseTheSameTagNameForTheInterfaceAndTheMethod_issue11570() throws IOException {
    final Map<String, File> output = generateFromContract("src/test/resources/spring.av/spring/3_0/bugs/issue_11570.yml", SPRING_BOOT);

    final String expectedTagName = "\"personTagWithExclamation!\"";
    final String expectedTagDescription = "\"the personTagWithExclamation! API\"";

    final String interfaceTag = "@Tag(name = " + expectedTagName + ", description = " + expectedTagDescription + ")";
    final String methodTag = "tags = { " + expectedTagName + " }";
    assertFileContains(output.get("Personv10Api.java").toPath(), interfaceTag, methodTag);
  }

  @Test
  public void shouldGenerateConstructorWithOnlyRequiredParameters() throws IOException {
    final Map<String, File> output = generateFromContract("src/test/resources/spring.av/spring/3_0/issue_9789.yml", SPRING_BOOT);

    JavaFileAssert.assertThat(output.get("ObjectWithNoRequiredParameterv10.java")).assertNoConstructor("String");

    JavaFileAssert.assertThat(output.get("ObjectWithRequiredParameterv10.java")).assertConstructor();
    JavaFileAssert.assertThat(output.get("ObjectWithRequiredParameterv10.java")).assertConstructor("String", "String")
        .hasParameter("param2").toConstructor()
        .hasParameter("param3");

    JavaFileAssert.assertThat(output.get("ObjectWithInheritedRequiredParameterv10.java")).assertConstructor();
    JavaFileAssert.assertThat(output.get("ObjectWithInheritedRequiredParameterv10.java")).assertConstructor("Integer", "String", "String")
        .hasParameter("param2").toConstructor()
        .hasParameter("param3").toConstructor()
        .hasParameter("param6").toConstructor()
        .bodyContainsLines("super(param2, param3)", "this.param6 = param6");
  }

  private Map<String, File> generateFromContract(String url, String library) throws IOException {
    return generateFromContract(url, library, new HashMap<>());
  }

  private Map<String, File> generateFromContract(String url, String library, Map<String, Object> additionalProperties) throws IOException {
    File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();

    OpenAPI openAPI = new OpenAPIParser()
        .readLocation(url, null, new ParseOptions()).getOpenAPI();

    SpringAvGenerator codegen = new SpringAvGenerator();
    if (null != library) {
      codegen.setLibrary(library);
    }
    codegen.setOutputDir(output.getAbsolutePath());
    codegen.additionalProperties().putAll(additionalProperties);

    ClientOptInput input = new ClientOptInput()
        .openAPI(openAPI)
        .config(codegen);

    DefaultGenerator generator = new DefaultGenerator();

    return generator.opts(input).generate().stream()
        .collect(Collectors.toMap(File::getName, Function.identity()));
  }

  @Test
  public void testMappingSubtypesIssue13150() throws IOException {
    File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();
    String outputPath = output.getAbsolutePath().replace('\\', '/');
    OpenAPI openAPI = new OpenAPIParser()
        .readLocation("src/test/resources/spring.av/spring/3_0/bugs/issue_13150.yaml", null, new ParseOptions()).getOpenAPI();

    SpringAvGenerator codegen = new SpringAvGenerator();
    codegen.setOutputDir(output.getAbsolutePath());
    codegen.additionalProperties().put(CXFServerFeatures.LOAD_TEST_DATA_FROM_FILE, "true");
    codegen.setUseOneOfInterfaces(true);

    ClientOptInput input = new ClientOptInput();
    input.openAPI(openAPI);
    input.config(codegen);

    DefaultGenerator generator = new DefaultGenerator();
    codegen.setHateoas(true);
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.LEGACY_DISCRIMINATOR_BEHAVIOR, "false");

    codegen.setUseOneOfInterfaces(true);
    codegen.setLegacyDiscriminatorBehavior(false);

    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");

    generator.opts(input).generate();

    String jsonSubType = "@JsonSubTypes({\n" +
        "  @JsonSubTypes.Type(value = Foov10.class, name = \"foo\")\n" +
        "})";
    assertFileContains(Paths.get(outputPath + "/src/main/java/org/openapitools/model/Parentv10.java"), jsonSubType);
  }

  @Test
  public void shouldGenerateJsonPropertyAnnotationLocatedInGetters_issue5705() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/spring/3_0/petstore-with-fake-endpoints-models-for-testing.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    Map<String, File> files = generator.opts(configurationPair.getKey()).generate().stream()
        .collect(Collectors.toMap(File::getName, Function.identity()));

    JavaFileAssert.assertThat(files.get("ResponseObjectWithDifferentFieldNamesv10.java"))
        .hasProperty("normalPropertyName")
        .assertPropertyAnnotations()
        .doesNotContainsWithName("JsonProperty")
        .toProperty().toType()
        .hasProperty("UPPER_CASE_PROPERTY_SNAKE")
        .assertPropertyAnnotations()
        .doesNotContainsWithName("JsonProperty")
        .toProperty().toType()
        .hasProperty("lowerCasePropertyDashes")
        .assertPropertyAnnotations()
        .doesNotContainsWithName("JsonProperty")
        .toProperty().toType()
        .hasProperty("propertyNameWithSpaces")
        .assertPropertyAnnotations()
        .doesNotContainsWithName("JsonProperty")
        .toProperty().toType()
        .assertMethod("getNormalPropertyName")
        .assertMethodAnnotations()
        .containsWithNameAndAttributes("JsonProperty", ImmutableMap.of("value", "\"normalPropertyName\""))
        .toMethod().toFileAssert()
        .assertMethod("getUPPERCASEPROPERTYSNAKE")
        .assertMethodAnnotations()
        .containsWithNameAndAttributes("JsonProperty", ImmutableMap.of("value", "\"UPPER_CASE_PROPERTY_SNAKE\""))
        .toMethod().toFileAssert()
        .assertMethod("getLowerCasePropertyDashes")
        .assertMethodAnnotations()
        .containsWithNameAndAttributes("JsonProperty", ImmutableMap.of("value", "\"lower-case-property-dashes\""))
        .toMethod().toFileAssert()
        .assertMethod("getPropertyNameWithSpaces")
        .assertMethodAnnotations()
        .containsWithNameAndAttributes("JsonProperty", ImmutableMap.of("value", "\"property name with spaces\""));
  }

  @Test
  public void integerValueAsDefaultVersion() throws IOException, InterruptedException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/spring/3_0/integer_as_default.yml", SPRING_BOOT, false);

    DefaultGenerator generator = new DefaultGenerator();

    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");
    generator.opts(configurationPair.getKey()).generate();

    JavaFileAssert.assertThat(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Zebrasv1Api.java"))
        .assertTypeAnnotations()
        .hasSize(3)
        .containsWithName("Validated")
        .containsWithName("Generated")
        .containsWithNameAndAttributes("Generated", ImmutableMap.of(
            "value", "\"com.endava.spring.SpringAvGenerator\""
        ))
        .containsWithNameAndAttributes("Tag", ImmutableMap.of(
            "name", "\"zebrasv1\""
        ))
        .toType()
        .assertMethod("getZebrasv1")
        .hasReturnType("ResponseEntity<Void>")
        .assertMethodAnnotations()
        .hasSize(2)
        .containsWithNameAndAttributes("Operation", ImmutableMap.of("operationId", "\"getZebrasv1\""))
        .containsWithNameAndAttributes("RequestMapping", ImmutableMap.of(
            "method", "RequestMethod.GET",
            "value", "\"/v1/zebras\""
        ))
        .toMethod()
        .hasParameter("limit").withType("BigDecimal")
        .assertParameterAnnotations()
        .containsWithName("Valid")
        .containsWithNameAndAttributes("Parameter", ImmutableMap.of("name", "\"limit\""))
        .containsWithNameAndAttributes("RequestParam", ImmutableMap.of("required", "false", "value", "\"limit\""))
        .toParameter()
        .toMethod()
        .hasParameter("animalParams").withType("AnimalParamsv1")
        .toMethod()
        .commentContainsLines("GET /v1/zebras", "@param limit (optional)")
        .bodyContainsLines("return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED)");

    JavaFileAssert.assertThat(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/AnimalParamsv1.java"))
        .hasImports("org.springframework.format.annotation.DateTimeFormat")
        .hasProperty("born").withType("LocalDate")
        .assertPropertyAnnotations()
        .containsWithNameAndAttributes("DateTimeFormat", ImmutableMap.of("iso", "DateTimeFormat.ISO.DATE"))
        .toProperty()
        .toType()
        .hasProperty("lastSeen").withType("OffsetDateTime")
        .assertPropertyAnnotations()
        .containsWithNameAndAttributes("DateTimeFormat", ImmutableMap.of("iso", "DateTimeFormat.ISO.DATE_TIME"))
        .toProperty().toType()
        .assertMethod("born", "LocalDate")
        .bodyContainsLines("this.born = born")
        .doesNotHaveComment();
  }

  private Pair<ClientOptInput, String> configureSpringAvGeneratorTests(String filePath, String library, boolean validateSpec) throws IOException{
    File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
    output.deleteOnExit();
    String outputPath = output.getAbsolutePath().replace('\\', '/');

    CodegenConfigurator configurator = new CodegenConfigurator();
    configurator.setInputSpec(filePath);
    configurator.setGeneratorName("spring-av");
    configurator.setLibrary(library);
    configurator.setOutputDir(output.getAbsolutePath());
    configurator.setValidateSpec(validateSpec);

    final ClientOptInput input = configurator.toClientOptInput();

    return Pair.of(input, outputPath);
  }

  @Test
  public void defaultVersion() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/default_version.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.opts(configurationPair.getKey()).generate();

    JavaFileAssert.assertThat(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/ResponseSchemav10.java"));
    JavaFileAssert.assertThat(Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv10Api.java"))
        .hasImports("org.openapitools.model.ResponseSchemav10")
        .assertMethod("eventsv10Get");
  }

  @Test
  public void operationFromToVersion() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/operation_from_to_version.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.opts(configurationPair.getKey()).generate();

    Path versionOneApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv10Api.java");
    JavaFileAssert.assertThat(versionOneApiFile)
            .assertMethod("eventsv10Post");
    assertFileNotContains(versionOneApiFile, "eventsv10Get");
    assertFileContains(versionOneApiFile, "/v1.0/events");

    Path versionTwoApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv20Api.java");
    JavaFileAssert.assertThat(versionTwoApiFile)
            .assertMethod("eventsv20Get").toFileAssert()
            .assertMethod("eventsv20Post");
    assertFileContains(versionTwoApiFile, "/v2.0/events");

    Path versionThreeApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv30Api.java");
    JavaFileAssert.assertThat(versionThreeApiFile)
            .assertMethod("eventsv30Get").toFileAssert()
            .assertMethod("eventsv30Post");
    assertFileContains(versionThreeApiFile, "/v3.0/events");
  }

  @Test
  public void excludeOperationInVersion() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/operation_excluded_in_version.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.opts(configurationPair.getKey()).generate();

    Path versionOneApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv10Api.java");
    JavaFileAssert.assertThat(versionOneApiFile)
        .assertMethod("getEventsv10").toFileAssert()
        .assertMethod("createEventv10");

    Path versionTwoApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv20Api.java");
    JavaFileAssert.assertThat(versionTwoApiFile)
        .assertMethod("createEventv20");
    assertFileNotContains(versionTwoApiFile, "getEventsv20");
  }

  @Test
  public void operationFromVersion() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/operation_from_version.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.opts(configurationPair.getKey()).generate();

    Path versionOneApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv10Api.java");
    assertFileNotExists(versionOneApiFile);

    Path versionTwoApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv20Api.java");
    JavaFileAssert.assertThat(versionTwoApiFile)
        .assertMethod("getEventsv20");
    assertFileNotContains(versionTwoApiFile, "createEventv20");

    Path versionThreeApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv30Api.java");
    JavaFileAssert.assertThat(versionThreeApiFile)
        .assertMethod("getEventsv30").toFileAssert()
        .assertMethod("createEventv30");
  }

  @Test
  public void operationToVersion() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/operation_to_version.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.opts(configurationPair.getKey()).generate();

    Path versionOneApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv10Api.java");
    JavaFileAssert.assertThat(versionOneApiFile)
        .assertMethod("getEventsv10");
    assertFileNotContains(versionOneApiFile, "createEventv10");

    Path versionTwoApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv20Api.java");
    JavaFileAssert.assertThat(versionTwoApiFile)
        .assertMethod("createEventv20").toFileAssert()
        .assertMethod("getEventsv20");

    Path versionThreeApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv30Api.java");
    JavaFileAssert.assertThat(versionThreeApiFile)
        .assertMethod("createEventv30").toFileAssert();
    assertFileNotContains(versionTwoApiFile, "getEventsv30");
  }

  @Test
  public void definitionAtSchemaLevel() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/definition_at_schema_level.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "false");
    generator.opts(configurationPair.getKey()).generate();

    Path versionOneModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv10.java");
    JavaFileAssert.assertThat(versionOneModelFile)
        .assertMethod("getFirstName").toFileAssert()
        .assertMethod("getLastName").toFileAssert()
        .assertMethod("getEmail").toFileAssert()
        .assertMethod("getTitle");

    Path versionTwoModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv20.java");
    JavaFileAssert.assertThat(versionTwoModelFile)
        .assertMethod("getFirstName").toFileAssert()
        .assertMethod("getLastName").toFileAssert()
        .assertMethod("getEmail");
    assertFileContains(versionTwoModelFile, "Example email", "example@gmail.com");
    assertFileNotContains(versionTwoModelFile, "Email of the speaker", "dan.kolov@gmail.com");
    assertFileNotContains(versionTwoModelFile, "title");

    Path versionThreeModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv30.java");
    JavaFileAssert.assertThat(versionThreeModelFile)
        .assertMethod("getFirstName").toFileAssert()
        .assertMethod("getLastName");
    assertFileNotContains(versionThreeModelFile, "title", "email");
  }

  @Test
  public void testSchemaPropertyFromOnly() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/schema_test_from.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "false");
    generator.opts(configurationPair.getKey()).generate();

    Path version1ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv1.java");
    Path version2ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv2.java");
    Path version3ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv3.java");
    Path version4ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv4.java");

    //v1
    assertFileContains(version1ModelFile, "firstName");
    assertFileContains(version1ModelFile, "lastName");
    assertFileNotContains(version1ModelFile, "email");
    assertFileNotContains(version1ModelFile, "title");
    assertFileNotContains(version1ModelFile, "yearsInCompany");
    //v2
    assertFileContains(version2ModelFile, "firstName");
    assertFileContains(version2ModelFile, "lastName");
    assertFileContains(version2ModelFile, "email");
    assertFileNotContains(version2ModelFile, "title");
    assertFileNotContains(version2ModelFile, "yearsInCompany");
    //v3
    assertFileContains(version3ModelFile, "firstName");
    assertFileContains(version3ModelFile, "lastName");
    assertFileContains(version3ModelFile, "email");
    assertFileContains(version3ModelFile, "title");
    assertFileNotContains(version3ModelFile, "yearsInCompany");
    //v4
    assertFileContains(version4ModelFile, "firstName");
    assertFileContains(version4ModelFile, "lastName");
    assertFileContains(version4ModelFile, "email");
    assertFileContains(version4ModelFile, "title");
    assertFileContains(version4ModelFile, "yearsInCompany");
  }

  @Test
  public void testSchemaPropertyToOnly() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/schema_test_to.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "false");
    generator.opts(configurationPair.getKey()).generate();

    Path version1ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv1.java");
    Path version2ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv2.java");
    Path version3ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv3.java");
    Path version4ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv4.java");

    //v1
    assertFileContains(version1ModelFile, "firstName");
    assertFileContains(version1ModelFile, "lastName");
    assertFileContains(version1ModelFile, "email");
    assertFileContains(version1ModelFile, "title");
    assertFileContains(version1ModelFile, "yearsInCompany");
    //v2
    assertFileContains(version2ModelFile, "firstName");
    assertFileContains(version2ModelFile, "lastName");
    assertFileNotContains(version2ModelFile, "email");
    assertFileContains(version2ModelFile, "title");
    assertFileContains(version2ModelFile, "yearsInCompany");
    //v3
    assertFileContains(version3ModelFile, "firstName");
    assertFileContains(version3ModelFile, "lastName");
    assertFileNotContains(version3ModelFile, "email");
    assertFileNotContains(version3ModelFile, "title");
    assertFileContains(version3ModelFile, "yearsInCompany");
    //v4
    assertFileContains(version4ModelFile, "firstName");
    assertFileContains(version4ModelFile, "lastName");
    assertFileNotContains(version4ModelFile, "email");
    assertFileNotContains(version4ModelFile, "title");
    assertFileNotContains(version4ModelFile, "yearsInCompany");
  }

  @Test
  public void testSchemaPropertyExcludeOnly() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/schema_test_exclude.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "false");
    generator.opts(configurationPair.getKey()).generate();

    Path version1ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv1.java");
    Path version2ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv2.java");
    Path version3ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv3.java");
    Path version4ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv4.java");

    //v1
    assertFileContains(version1ModelFile, "firstName");
    assertFileNotContains(version1ModelFile, "lastName");
    assertFileContains(version1ModelFile, "email");
    assertFileContains(version1ModelFile, "title");
    assertFileContains(version1ModelFile, "yearsInCompany");
    //v2
    assertFileContains(version2ModelFile, "firstName");
    assertFileContains(version2ModelFile, "lastName");
    assertFileNotContains(version2ModelFile, "email");
    assertFileContains(version2ModelFile, "title");
    assertFileContains(version2ModelFile, "yearsInCompany");
    //v3
    assertFileContains(version3ModelFile, "firstName");
    assertFileContains(version3ModelFile, "lastName");
    assertFileContains(version3ModelFile, "email");
    assertFileNotContains(version3ModelFile, "title");
    assertFileContains(version3ModelFile, "yearsInCompany");
    //v4
    assertFileContains(version4ModelFile, "firstName");
    assertFileContains(version4ModelFile, "lastName");
    assertFileContains(version4ModelFile, "email");
    assertFileContains(version4ModelFile, "title");
    assertFileNotContains(version4ModelFile, "yearsInCompany");
  }

  @Test
  public void testSchemaPropertyAllTogether() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/schema_test_all_together.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "false");
    generator.opts(configurationPair.getKey()).generate();

    Path version1ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv1.java");
    Path version2ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv2.java");
    Path version3ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv3.java");
    Path version4ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv4.java");
    Path version5ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv5.java");

    //v1
    assertFileContains(version1ModelFile, "firstName");
    assertFileContains(version1ModelFile, "lastName");
    assertFileNotContains(version1ModelFile, "email");
    assertFileNotContains(version1ModelFile, "title");
    assertFileNotContains(version1ModelFile, "yearsInCompany");
    //v2
    assertFileContains(version2ModelFile, "firstName");
    assertFileContains(version2ModelFile, "lastName");
    assertFileNotContains(version2ModelFile, "email");
    assertFileContains(version2ModelFile, "title");
    assertFileContains(version2ModelFile, "yearsInCompany");
    //v3
    assertFileContains(version3ModelFile, "firstName");
    assertFileContains(version3ModelFile, "lastName");
    assertFileContains(version3ModelFile, "email");
    assertFileNotContains(version3ModelFile, "title");
    assertFileNotContains(version3ModelFile, "yearsInCompany");
    //v4
    assertFileContains(version4ModelFile, "firstName");
    assertFileNotContains(version4ModelFile, "lastName");
    assertFileNotContains(version4ModelFile, "email");
    assertFileContains(version4ModelFile, "title");
    assertFileNotContains(version4ModelFile, "yearsInCompany");
    //v5
    assertFileContains(version5ModelFile, "firstName");
    assertFileNotContains(version5ModelFile, "lastName");
    assertFileContains(version5ModelFile, "email");
    assertFileContains(version5ModelFile, "title");
    assertFileContains(version5ModelFile, "yearsInCompany");
  }

  @Test
  public void testSchemaPropertyAllTogetherDecimal() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/schema_test_all_together_decimal.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "false");
    generator.opts(configurationPair.getKey()).generate();

    Path version10ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv10.java");
    Path version12ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv12.java");
    Path version16ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv16.java");
    Path version43ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv43.java");
    Path version51ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv51.java");

    //v1
    assertFileContains(version10ModelFile, "firstName");
    assertFileContains(version10ModelFile, "lastName");
    assertFileNotContains(version10ModelFile, "email");
    assertFileNotContains(version10ModelFile, "title");
    assertFileNotContains(version10ModelFile, "yearsInCompany");
    //v2
    assertFileContains(version12ModelFile, "firstName");
    assertFileContains(version12ModelFile, "lastName");
    assertFileNotContains(version12ModelFile, "email");
    assertFileContains(version12ModelFile, "title");
    assertFileContains(version12ModelFile, "yearsInCompany");
    //v3
    assertFileContains(version16ModelFile, "firstName");
    assertFileContains(version16ModelFile, "lastName");
    assertFileContains(version16ModelFile, "email");
    assertFileNotContains(version16ModelFile, "title");
    assertFileNotContains(version16ModelFile, "yearsInCompany");
    //v4
    assertFileContains(version43ModelFile, "firstName");
    assertFileNotContains(version43ModelFile, "lastName");
    assertFileNotContains(version43ModelFile, "email");
    assertFileContains(version43ModelFile, "title");
    assertFileNotContains(version43ModelFile, "yearsInCompany");
    //v5
    assertFileContains(version51ModelFile, "firstName");
    assertFileNotContains(version51ModelFile, "lastName");
    assertFileContains(version51ModelFile, "email");
    assertFileContains(version51ModelFile, "title");
    assertFileContains(version51ModelFile, "yearsInCompany");
  }

  @Test
  public void operationDefinition() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/operation_definition.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.opts(configurationPair.getKey()).generate();

    Path versionOneApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv10Api.java");
    JavaFileAssert.assertThat(versionOneApiFile)
        .assertMethod("getEventsv10")
        .hasReturnType("ResponseEntity<List<ResponseSchemav10>>")
        .assertMethodAnnotations()
        .containsWithNameAndAttributes("RequestMapping", ImmutableMap.of(
            "method", "RequestMethod.GET",
            "value", "\"/v1.0/events\""
        ));

    assertFileContains(versionOneApiFile, "operationId = \"getEventsv10\","
        + " summary = \"Returns all events.\","
        + " responses = { @ApiResponse(responseCode = \"200\", description = \"OK\", content = { @Content(mediaType = \"application/json\","
        + " array = @ArraySchema(schema = @Schema(implementation = ResponseSchemav10.class)))");

    Path versionTwoApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv20Api.java");
    JavaFileAssert.assertThat(versionTwoApiFile)
        .assertMethod("getEventsv20")
        .hasReturnType("ResponseEntity<List<ResponseFromDefinitionv20>>")
        .assertMethodAnnotations()
        .containsWithNameAndAttributes("RequestMapping", ImmutableMap.of(
            "method", "RequestMethod.GET",
            "value", "\"/v2.0/events\""
        ));

    assertFileContains(versionTwoApiFile, "operationId = \"getEventsv20\","
        + " summary = \"Returns all events.\","
        + " responses = { @ApiResponse(responseCode = \"200\", description = \"OK in definition\", content = { @Content(mediaType = \"application/json\","
        + " array = @ArraySchema(schema = @Schema(implementation = ResponseFromDefinitionv20.class)))");

    Path versionThreeApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv30Api.java");
    JavaFileAssert.assertThat(versionThreeApiFile)
        .assertMethod("getEventsv30")
        .hasReturnType("ResponseEntity<List<ResponseSchemav30>>")
        .assertMethodAnnotations()
        .containsWithNameAndAttributes("RequestMapping", ImmutableMap.of(
            "method", "RequestMethod.GET",
            "value", "\"/v3.0/events\""
        ))
        .containsWithName("Deprecated");

    assertFileContains(versionThreeApiFile, "operationId = \"getEventsv30\","
        + " summary = \"Returns all events.\","
        + " responses = { @ApiResponse(responseCode = \"200\", description = \"OK\", content = { @Content(mediaType = \"application/json\","
        + " array = @ArraySchema(schema = @Schema(implementation = ResponseSchemav30.class)))");
  }

  @Test
  public void operationDeleteInDefinition() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/operation_delete_in_definition.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.opts(configurationPair.getKey()).generate();

    Path versionOneApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv10Api.java");
    JavaFileAssert.assertThat(versionOneApiFile)
        .assertMethod("getEventsv10")
        .hasReturnType("ResponseEntity<ResponseSchemav10>")
        .assertMethodAnnotations()
        .containsWithNameAndAttributes("RequestMapping", ImmutableMap.of(
            "method", "RequestMethod.GET",
            "value", "\"/v1.0/events\""
        ));

    assertFileContains(versionOneApiFile, "operationId = \"getEventsv10\","
        + " summary = \"Returns all events.\","
        + " responses = { @ApiResponse(responseCode = \"200\", description = \"OK\", content = { @Content(mediaType = \"application/json\","
        + " schema = @Schema(implementation = ResponseSchemav10.class)) })");

    Path versionTwoApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv20Api.java");
    JavaFileAssert.assertThat(versionTwoApiFile)
        .assertMethod("getEventsv20")
        .hasReturnType("ResponseEntity<Void>")
        .assertMethodAnnotations()
        .containsWithNameAndAttributes("RequestMapping", ImmutableMap.of(
            "method", "RequestMethod.GET",
            "value", "\"/v2.0/events\""
        ));

    assertFileContains(versionTwoApiFile, "operationId = \"getEventsv20\","
        + " summary = \"Returns all events.\","
        + " responses = { }");
  }

  @Test
  public void parameterFromToVersion() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/parameter_from_to_version.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.opts(configurationPair.getKey()).generate();

    Path versionOneApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv10Api.java");
    JavaFileAssert.assertThat(versionOneApiFile)
        .assertMethod("getEventsv10")
        .hasParameter("param1").toMethod()
        .doesNotHaveParameter("param2");

    Path versionTwoApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv20Api.java");
    JavaFileAssert.assertThat(versionTwoApiFile)
        .assertMethod("getEventsv20")
        .hasParameter("param1").toMethod()
        .hasParameter("param2");

    Path versionThreeApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv30Api.java");
    JavaFileAssert.assertThat(versionThreeApiFile)
        .assertMethod("getEventsv30")
        .hasParameter("param2").toMethod()
        .doesNotHaveParameter("param1");
  }

  @Test
  public void parameter_excluded_in_version() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/parameter_excluded_in_version.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.opts(configurationPair.getKey()).generate();

    Path versionOneApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv10Api.java");
    JavaFileAssert.assertThat(versionOneApiFile)
        .assertMethod("getEventsv10")
        .hasParameter("param1");

    Path versionTwoApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv21Api.java");
    JavaFileAssert.assertThat(versionTwoApiFile)
        .assertMethod("getEventsv21")
        .doesNotHaveParameter("param1");
  }

  @Test
  public void overrideParameter() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/override_parameter.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.opts(configurationPair.getKey()).generate();

    Path versionOneApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv10Api.java");
    JavaFileAssert.assertThat(versionOneApiFile)
            .assertMethod("getEventsv10")
            .doesNotHaveParameter("param1");
    assertFileNotContains(versionOneApiFile, "Param at path level");

    Path versionTwoApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv20Api.java");
    JavaFileAssert.assertThat(versionTwoApiFile)
            .assertMethod("getEventsv20")
            .hasParameter("param1")
            .withType("String");
    assertFileContains(versionTwoApiFile, "Overridden param in the get operation");
    assertFileNotContains(versionTwoApiFile, "Param at path level");

    Path versionThreeApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv30Api.java");
    JavaFileAssert.assertThat(versionThreeApiFile)
            .assertMethod("getEventsv30")
            .hasParameter("param1")
            .withType("String");
    assertFileContains(versionTwoApiFile, "Overridden param in the get operation");
    assertFileNotContains(versionTwoApiFile, "Param at path level");
  }

  @Test
  public void parameterDefinition() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/parameter_definition.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.opts(configurationPair.getKey()).generate();

    Path versionOneApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv10Api.java");
    JavaFileAssert.assertThat(versionOneApiFile)
        .assertMethod("getEventsv10")
        .hasParameter("param1")
        .withType("Object").toMethod()
        .hasParameter("param3")
        .withType("String").toMethod()
        .doesNotHaveParameter("param2");
    assertFileContains(versionOneApiFile, "Param at path level");
    assertFileContains(versionOneApiFile, "Param in get operation");
    assertFileNotContains(versionOneApiFile, "Param in definition");

    Path versionTwoApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Eventsv20Api.java");
    JavaFileAssert.assertThat(versionTwoApiFile)
        .assertMethod("getEventsv20")
        .hasParameter("param2")
        .withType("String").toMethod()
        .doesNotHaveParameter("param1")
        .doesNotHaveParameter("param3");
    assertFileNotContains(versionTwoApiFile, "Param at path level");
    assertFileNotContains(versionTwoApiFile, "Param in get operation");
    assertFileContains(versionTwoApiFile, "Param in definition");
  }

  @Test
  public void definitionAtSchemasLevelUsedForResponseMapping() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/definition_at_schema_level_for_responses.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.opts(configurationPair.getKey()).generate();

    Path versionOneModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/SpeakerResponsev10.java");
    JavaFileAssert.assertThat(versionOneModelFile)
        .assertMethod("getFirstName");
    assertFileContains(versionOneModelFile, "Speaker Response 1 description");
    assertFileNotContains(versionOneModelFile, "lastName", "email");

    Path versionTwoModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/SpeakerResponsev20.java");
    JavaFileAssert.assertThat(versionTwoModelFile)
        .assertMethod("getFirstName").toFileAssert()
        .assertMethod("getLastName");
    assertFileContains(versionTwoModelFile, "Speaker Response 2 description");
    assertFileNotContains(versionTwoModelFile, "email");

    Path versionThreeModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/SpeakerResponsev30.java");
    JavaFileAssert.assertThat(versionThreeModelFile)
        .assertMethod("getFirstName").toFileAssert()
        .assertMethod("getLastName").toFileAssert()
        .assertMethod("getEmail");
    assertFileContains(versionThreeModelFile, "Speaker Response 3 description");

    Path versionOneApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Speakersv10Api.java");
    assertFileContains(versionOneApiFile, "SpeakerResponsev10");
    assertFileNotContains(versionOneApiFile, "SpeakerResponsev20", "SpeakerResponsev30", "lastName", "email");

    Path versionTwoApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Speakersv20Api.java");
    assertFileContains(versionTwoApiFile, "SpeakerResponsev20");
    assertFileNotContains(versionTwoApiFile, "SpeakerResponsev10", "SpeakerResponsev30", "email");

    Path versionThreeApiFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/api/Speakersv30Api.java");
    assertFileContains(versionThreeApiFile, "SpeakerResponsev30");
    assertFileNotContains(versionThreeApiFile, "SpeakerResponsev10", "SpeakerResponsev20");
  }

  @Test
  public void testSchemaPropertyAllTogetherWithRequired() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/schema_test_required.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "false");
    generator.opts(configurationPair.getKey()).generate();

    Path version1ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv1.java");
    Path version2ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv2.java");
    Path version3ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv3.java");
    Path version4ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv4.java");

    //V1
    JavaFileAssert.assertThat(version1ModelFile)
        .assertMethod("getLastName")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    JavaFileAssert.assertThat(version1ModelFile)
        .assertMethod("getTitle")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    JavaFileAssert.assertThat(version1ModelFile)
        .assertMethod("getYearsInCompany")
        .assertMethodAnnotations()
        .containsWithName("NotNull");

    //V2
    JavaFileAssert.assertThat(version2ModelFile)
        .assertMethod("getFirstName")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    JavaFileAssert.assertThat(version2ModelFile)
        .assertMethod("getLastName")
        .assertMethodAnnotations()
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));

    JavaFileAssert.assertThat(version2ModelFile)
        .assertMethod("getEmail")
        .assertMethodAnnotations()
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));
    JavaFileAssert.assertThat(version2ModelFile)
        .assertMethod("getTitle")
        .assertMethodAnnotations()
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));

    JavaFileAssert.assertThat(version2ModelFile)
        .assertMethod("getYearsInCompany")
        .assertMethodAnnotations()
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));

    //V3
    JavaFileAssert.assertThat(version3ModelFile)
        .assertMethod("getFirstName")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    JavaFileAssert.assertThat(version3ModelFile)
        .assertMethod("getLastName")
        .assertMethodAnnotations()
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));

    JavaFileAssert.assertThat(version3ModelFile)
        .assertMethod("getEmail")
        .assertMethodAnnotations()
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));
    JavaFileAssert.assertThat(version3ModelFile)
        .assertMethod("getTitle")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    JavaFileAssert.assertThat(version3ModelFile)
        .assertMethod("getYearsInCompany")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    //V4
    JavaFileAssert.assertThat(version4ModelFile)
        .assertMethod("getFirstName")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    JavaFileAssert.assertThat(version4ModelFile)
        .assertMethod("getLastName")
        .assertMethodAnnotations()
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));

    JavaFileAssert.assertThat(version4ModelFile)
        .assertMethod("getEmail")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    JavaFileAssert.assertThat(version4ModelFile)
        .assertMethod("getTitle")
        .assertMethodAnnotations()
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));

    JavaFileAssert.assertThat(version4ModelFile)
        .assertMethod("getYearsInCompany")
        .assertMethodAnnotations()
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));
  }

  @Test
  public void testOperationSchemaPropertiesRequired() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/operation_schema_properties_required_test.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.opts(configurationPair.getKey()).generate();

    Path version1ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/CreateSpeakerRequestv1.java");
    Path version2ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/CreateSpeakerRequestv2.java");
    Path version3ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/CreateSpeakerRequestv3.java");
    Path version4ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/CreateSpeakerRequestv4.java");

    //V1
    JavaFileAssert.assertThat(version1ModelFile)
        .assertMethod("getLastName")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    JavaFileAssert.assertThat(version1ModelFile)
        .assertMethod("getTitle")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    JavaFileAssert.assertThat(version1ModelFile)
        .assertMethod("getYearsInCompany")
        .assertMethodAnnotations()
        .containsWithName("NotNull");

    //V2
    JavaFileAssert.assertThat(version2ModelFile)
        .assertMethod("getFirstName")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    JavaFileAssert.assertThat(version2ModelFile)
        .assertMethod("getLastName")
        .assertMethodAnnotations()
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));

    JavaFileAssert.assertThat(version2ModelFile)
        .assertMethod("getEmail")
        .assertMethodAnnotations()
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));
    JavaFileAssert.assertThat(version2ModelFile)
        .assertMethod("getTitle")
        .assertMethodAnnotations()
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));

    JavaFileAssert.assertThat(version2ModelFile)
        .assertMethod("getYearsInCompany")
        .assertMethodAnnotations()
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));

    //V3
    JavaFileAssert.assertThat(version3ModelFile)
        .assertMethod("getFirstName")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    JavaFileAssert.assertThat(version3ModelFile)
        .assertMethod("getLastName")
        .assertMethodAnnotations()
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));

    JavaFileAssert.assertThat(version3ModelFile)
        .assertMethod("getEmail")
        .assertMethodAnnotations()
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));
    JavaFileAssert.assertThat(version3ModelFile)
        .assertMethod("getTitle")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    JavaFileAssert.assertThat(version3ModelFile)
        .assertMethod("getYearsInCompany")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    //V4
    JavaFileAssert.assertThat(version4ModelFile)
        .assertMethod("getFirstName")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    JavaFileAssert.assertThat(version4ModelFile)
        .assertMethod("getLastName")
        .assertMethodAnnotations()
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));

    JavaFileAssert.assertThat(version4ModelFile)
        .assertMethod("getEmail")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    JavaFileAssert.assertThat(version4ModelFile)
        .assertMethod("getTitle")
        .assertMethodAnnotations()
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));

    JavaFileAssert.assertThat(version4ModelFile)
        .assertMethod("getYearsInCompany")
        .assertMethodAnnotations()
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));
  }

  @Test
  public void testSchemaPropertyAllTogetherWithRequiredOverride() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/schema_test_required_override.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "false");
    generator.opts(configurationPair.getKey()).generate();

    Path version1ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv1.java");
    Path version2ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv2.java");
    Path version3ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv3.java");
    Path version4ModelFile = Paths.get(configurationPair.getValue() + "/src/main/java/org/openapitools/model/Speakerv4.java");

    //V1
    JavaFileAssert.assertThat(version1ModelFile)
        .assertMethod("getFirstName")
        .assertMethodAnnotations()
        .containsWithName("NotNull");

    JavaFileAssert.assertThat(version1ModelFile)
        .assertMethod("getLastName")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    JavaFileAssert.assertThat(version1ModelFile)
        .assertMethod("getTitle")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    JavaFileAssert.assertThat(version1ModelFile)
        .assertMethod("getEmail")
        .assertMethodAnnotations()
        .containsWithName("NotNull");

    JavaFileAssert.assertThat(version1ModelFile)
        .assertMethod("getYearsInCompany")
        .assertMethodAnnotations()
        .containsWithName("NotNull");

    //V2
    JavaFileAssert.assertThat(version2ModelFile)
        .assertMethod("getFirstName")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    JavaFileAssert.assertThat(version2ModelFile)
        .assertMethod("getLastName")
        .assertMethodAnnotations()
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));

    JavaFileAssert.assertThat(version2ModelFile)
        .assertMethod("getEmail")
        .assertMethodAnnotations()
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));
    JavaFileAssert.assertThat(version2ModelFile)
        .assertMethod("getTitle")
        .assertMethodAnnotations()
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));

    JavaFileAssert.assertThat(version2ModelFile)
        .assertMethod("getYearsInCompany")
        .assertMethodAnnotations()
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));

    //V3
    JavaFileAssert.assertThat(version3ModelFile)
        .assertMethod("getFirstName")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    JavaFileAssert.assertThat(version3ModelFile)
        .assertMethod("getLastName")
        .assertMethodAnnotations()
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));

    JavaFileAssert.assertThat(version3ModelFile)
        .assertMethod("getEmail")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    JavaFileAssert.assertThat(version3ModelFile)
        .assertMethod("getTitle")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    JavaFileAssert.assertThat(version3ModelFile)
        .assertMethod("getYearsInCompany")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    //V4
    JavaFileAssert.assertThat(version4ModelFile)
        .assertMethod("getFirstName")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    JavaFileAssert.assertThat(version4ModelFile)
        .assertMethod("getLastName")
        .assertMethodAnnotations()
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));

    JavaFileAssert.assertThat(version4ModelFile)
        .assertMethod("getEmail")
        .assertMethodAnnotations()
        .anyMatch(annotation -> !annotation.getNameAsString().equals("NotNull"));

    JavaFileAssert.assertThat(version4ModelFile)
        .assertMethod("getTitle")
        .assertMethodAnnotations()
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));

    JavaFileAssert.assertThat(version4ModelFile)
        .assertMethod("getYearsInCompany")
        .assertMethodAnnotations()
        .containsWithName("NotNull")
        .containsWithNameAndAttributes("Schema", ImmutableMap.of(
            "requiredMode", "Schema.RequiredMode.REQUIRED"
        ));
  }

  @Test
  public void testSpringDocPropertiesFile() throws IOException {
    Pair<ClientOptInput, String> configurationPair = configureSpringAvGeneratorTests("src/test/resources/spring.av/springdoc_properties.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "false");
    generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "true");
    generator.opts(configurationPair.getKey()).generate();

    Path springDocPropertiesFile = Paths.get(configurationPair.getValue() + "/src/main/resources/springdoc.properties");
    assertFileContains(springDocPropertiesFile, "springdoc.api-docs.groups.enabled=true");
    assertFileContains(springDocPropertiesFile, "springdoc.group-configs[0].group=v1.0");
    assertFileContains(springDocPropertiesFile, "springdoc.group-configs[0].paths-to-match=/v1.0/**");
    assertFileContains(springDocPropertiesFile, "springdoc.group-configs[1].group=v2.0");
    assertFileContains(springDocPropertiesFile, "springdoc.group-configs[1].paths-to-match=/v2.0/**");
    assertFileContains(springDocPropertiesFile, "springdoc.group-configs[2].group=v3.0");
    assertFileContains(springDocPropertiesFile, "springdoc.group-configs[2].paths-to-match=/v3.0/**");
  }

  @Test
  public void testResponsesDefinition() throws IOException {
    Pair<ClientOptInput, String> pair = configureSpringAvGeneratorTests("src/test/resources/spring.av/responses_definition.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.opts(pair.getKey()).generate();

    Path v1ApiFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/api/Speakersv10Api.java");
    String response200v1 = "@ApiResponse(responseCode = \"200\", description = \"OK\", content = {" +
        " @Content(mediaType = \"application/json\", schema = @Schema(implementation = Speakerv10.class))" +
        " })";
    String response404v1 = "@ApiResponse(responseCode = \"404\", description = \"Unauthorized\", content = {\n" +
        " @Content(mediaType = \"application/json\", schema = @Schema(implementation = GetAllSpeakers404Responsev10.class))\n" +
        " })";
    assertFileContains(v1ApiFile, response200v1);
    assertFileContains(v1ApiFile, response404v1);

    Path v2ApiFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/api/Speakersv20Api.java");
    String response200v2 = "@ApiResponse(responseCode = \"200\", description = \"OK\", content = {" +
        " @Content(mediaType = \"application/json\", schema = @Schema(implementation = Speakerv20.class))" +
        " })";
    String response404v2 = "@ApiResponse(responseCode = \"404\", description = \"Unauthorized\", content = {\n" +
        " @Content(mediaType = \"application/json\", schema = @Schema(implementation = SpeakerNewv20.class))\n" +
        " })";
    assertFileContains(v2ApiFile, response200v2);
    assertFileContains(v2ApiFile, response404v2);

    Path v3ApiFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/api/Speakersv30Api.java");
    String response200v3 = "@ApiResponse(responseCode = \"200\", description = \"OK\", content = {" +
        " @Content(mediaType = \"application/json\", schema = @Schema(implementation = Speakerv30.class))" +
        " })";
    String response404v3 = "@ApiResponse(responseCode = \"404\", description = \"from definition 3\", content = {\n" +
        " @Content(mediaType = \"application/json\", schema = @Schema(implementation = GetAllSpeakers404Responsev30.class))\n" +
        " })";
    assertFileContains(v3ApiFile, response200v3);
    assertFileContains(v3ApiFile, response404v3);

    Path v1ModelFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/model/GetAllSpeakers404Responsev10.java");
    JavaFileAssert.assertThat(v1ModelFile)
        .hasProperty("codeDefault").toType()
        .hasProperty("messageDefault");

    Path v2ModelFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/model/SpeakerNewv20.java");
    JavaFileAssert.assertThat(v2ModelFile)
        .hasProperty("codeFromDefinitionTwo").toType()
        .hasProperty("messageFromDefinitionTwo");

    Path v3ModelFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/model/GetAllSpeakers404Responsev30.java");
    JavaFileAssert.assertThat(v3ModelFile)
        .hasProperty("codeFromDefinitionThree").toType()
        .hasProperty("messageFromDefinitionThree");
  }

  @Test
  public void testResponsesDefinitionRemoveContentType() throws IOException {
    Pair<ClientOptInput, String> pair = configureSpringAvGeneratorTests("src/test/resources/spring.av/responses_definition_remove_content_type.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.opts(pair.getKey()).generate();

    Path v1ApiFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/api/Speakersv10Api.java");
    String v1JsonMediaType = "@Content(mediaType = \"application/json\", schema = @Schema(implementation = SpeakerDefaultv10.class))";
    String v1XmlMediaType = "@Content(mediaType = \"application/xml\", schema = @Schema(implementation = SpeakerDefaultv10.class))";
    assertFileContains(v1ApiFile, v1JsonMediaType);
    assertFileContains(v1ApiFile, v1XmlMediaType);

    Path v2ApiFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/api/Speakersv20Api.java");
    String v2JsonMediaType = "@Content(mediaType = \"application/json\", schema = @Schema(implementation = Speakerv20.class))";
    assertFileContains(v2ApiFile, v2JsonMediaType);
    assertFileNotContains(v2ApiFile, "application/xml");

    Path v1ModelFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/model/SpeakerDefaultv10.java");
    JavaFileAssert.assertThat(v1ModelFile)
        .hasProperty("codeDefault").toType()
        .hasProperty("messageDefault");

    Path v2ModelFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/model/Speakerv20.java");
    JavaFileAssert.assertThat(v2ModelFile)
        .hasProperty("code").toType()
        .hasProperty("message");
  }

  @Test
  public void testResponsesDefinitionAddContentType() throws IOException {
    Pair<ClientOptInput, String> pair = configureSpringAvGeneratorTests("src/test/resources/spring.av/responses_definition_add_content_type.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.opts(pair.getKey()).generate();

    Path v1ApiFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/api/Speakersv10Api.java");
    String v1JsonMediaType = "@Content(mediaType = \"application/json\", schema = @Schema(implementation = SpeakerDefaultv10.class))";
    assertFileContains(v1ApiFile, v1JsonMediaType);
    assertFileNotContains(v1ApiFile, "application/xml");

    Path v2ApiFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/api/Speakersv20Api.java");
    String v2JsonMediaType = "@Content(mediaType = \"application/json\", schema = @Schema(implementation = SpeakerDefaultv20.class))";
    String v2XmlMediaType = "@Content(mediaType = \"application/xml\", schema = @Schema(implementation = SpeakerDefaultv20.class))";
    assertFileContains(v2ApiFile, v2JsonMediaType);
    assertFileContains(v2ApiFile, v2XmlMediaType);
  }

  @Test
  public void testResponsesDefinitionOnlyDescription() throws IOException {
    Pair<ClientOptInput, String> pair = configureSpringAvGeneratorTests("src/test/resources/spring.av/responses_definition_only_description.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.opts(pair.getKey()).generate();

    Path v1ApiFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/api/Speakersv10Api.java");
    String v1JsonMediaType = "@Content(mediaType = \"application/json\", schema = @Schema(implementation = SpeakerDefaultv10.class))";
    assertFileContains(v1ApiFile, v1JsonMediaType);
    assertFileContains(v1ApiFile, "Unauthorized");

    Path v2ApiFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/api/Speakersv20Api.java");
    String v2JsonMediaType = "@Content(mediaType = \"application/json\", schema = @Schema(implementation = SpeakerDefaultv20.class))";
    assertFileContains(v2ApiFile, v2JsonMediaType);
    assertFileContains(v2ApiFile, "from definition 2");
  }

  @Test
  public void testRequestbodyDefinition() throws IOException {
    Pair<ClientOptInput, String> pair = configureSpringAvGeneratorTests("src/test/resources/spring.av/requestbody_definition_content_type.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.opts(pair.getKey()).generate();

    Path v1ApiFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/api/Speakersv10Api.java");
    assertFileContains(v1ApiFile, "SpeakerRequestSchemaDefaultv10");

    Path v2ApiFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/api/Speakersv20Api.java");
    assertFileContains(v2ApiFile, "GetAllSpeakersRequestv20");
    assertFileContains(v2ApiFile, "Request description from definition 2");

    Path v4ApiFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/api/Speakersv40Api.java");
    assertFileContains(v4ApiFile, "SpeakerRequestSchemav40");

    Path v1ModelFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/model/SpeakerRequestSchemaDefaultv10.java");
    JavaFileAssert.assertThat(v1ModelFile)
        .hasProperty("nameDefault");

    Path v2ModelFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/model/GetAllSpeakersRequestv20.java");
    JavaFileAssert.assertThat(v2ModelFile)
        .hasProperty("propertyFromDefinitionTwo");

    Path v4ModelFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/model/SpeakerRequestSchemav40.java");
    JavaFileAssert.assertThat(v4ModelFile)
        .hasProperty("nameFromDefinitionFour");
  }

  @Test
  public void testRequestbodyDefinition2() throws IOException {
    Pair<ClientOptInput, String> pair = configureSpringAvGeneratorTests("src/test/resources/spring.av/requestbody_definition_description.yaml", SPRING_BOOT, true);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
    generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
    generator.opts(pair.getKey()).generate();

    Path v1ApiFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/api/Speakersv10Api.java");
    assertFileContains(v1ApiFile, "GetAllSpeakersRequestv10");
    assertFileContains(v1ApiFile, "@RequestBody(required = false)");

    Path v2ApiFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/api/Speakersv20Api.java");
    assertFileContains(v2ApiFile, "GetAllSpeakersRequestv20");
    assertFileContains(v2ApiFile, "Request description from definition 2");
    assertFileContains(v2ApiFile, "@RequestBody(required = false)");

    Path v3ApiFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/api/Speakersv30Api.java");
    assertFileContains(v3ApiFile, "GetAllSpeakersRequestv30");
    assertFileContains(v3ApiFile, "Request description from definition 3");
    assertFileNotContains(v3ApiFile, "@RequestBody(required = false)");

    Path v1ModelFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/model/GetAllSpeakersRequestv10.java");
    JavaFileAssert.assertThat(v1ModelFile)
        .hasProperty("defaultProperty");

    Path v2ModelFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/model/GetAllSpeakersRequestv20.java");
    JavaFileAssert.assertThat(v2ModelFile)
        .hasProperty("defaultProperty");

    Path v3ModelFile = Paths.get(pair.getValue() + "/src/main/java/org/openapitools/model/GetAllSpeakersRequestv30.java");
    JavaFileAssert.assertThat(v3ModelFile)
        .hasProperty("defaultProperty");
  }
}
