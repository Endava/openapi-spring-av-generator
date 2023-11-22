
# Spring AV Code Generator

Spring AV is a plugin library built on top of [Java Spring code generator](https://openapi-generator.tech/docs/generators/spring). The main idea is to support URL path API versioning like:

```
/api/v1/users
```
or

```
/api/v1.0/users
```

Minor versions are supported as well: 

```
/api/v1.1/users
```

## General Info

Spring AV is providing a Vendor Extension property:

```yaml
 x-av-versions
```

This property is an object and owns properties like **from**, **to** and **exclude**:

```yaml
x-av-versions:
  from: 2.0
  to: 10.0
  exclude: [5.0, 6.0]
```

The AV treats **info.version** value as the default version for this specification. It must be a numeric value as AV does not support string versions. [Check Limitations](#limitations) section for more info.
```yaml
openapi: "3.0.2"
info:
  title: Example Project
  version: "1.0"
paths:
...
```

If there is no value in  **info.version** field Spring AV will use **1.0** as default. So each next version should be greater than the default one.

The library will generate Models and APIs files for **each version mentioned** in the file. If "use-tags" or "spring-cloud" library options are used the API file is one containing all versions.

## Installation

Spring AV Generator could be used as a Maven plug-in dependency like this:

```xml
<plugin>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-generator-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>generate-client-code</id>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <generatorName>spring-av</generatorName>
                <output>${project.basedir}</output>
                <inputSpec>\path\to\the\openapi\contract\contract.yml</inputSpec>
            </configuration>
        </execution>
    </executions>
    <dependencies>
        <dependency>
            <groupId>com.endava</groupId>
            <artifactId>spring-av-openapi-generator</artifactId>
            <version>1.0.1</version>
        </dependency>
    </dependencies>
</plugin>
```

Or could be used together with open api-generator CLI like this:

```
java -cp "/path/to/spring-av/library/jar/spring-av-openapi-generator-1.0.0.jar;/path/to/generator/jar/versions/6.5.0.jar" org.openapitools.codegen.OpenAPIGenerator generate -i /path/to/the/contract/contract.yml -g spring-av -o /path/to/output/folder
```

More information about this can be found in [Open API documentation](https://openapi-generator.tech/docs/customization/#use-your-new-generator-with-the-cli).

## Vendor Extension

Here are the supported properties in **x-av-versions**:

| Property    | Type        | Required | Description                                                                                                                                                                                               |
|-------------|-------------|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| from        | Number      | false    | The first version this property is active on - **inclusive** .                                                                                                                                            |
| to          | Number      | false    | The last version this property is inactive on - **inclusive**.                                                                                                                                            |
| exclude     | NumberArray | false    | An array of versions in which this **operation** or **property** is inactive                                                                                                                              |
| required    | Object      | false    | An object which used to mark if a **schema property** is required. It's properties are again - **from**, **to** and **exclude**. They work in the very same way as described above.                       |
| definitions | Object      | false    | A map of version as a key and field which will be overridden from this version on as a value. This mappings is always based on the "default" definition, there is no inheritance between mapped versions. |


## Usage and Examples

### Enable Operation: From-To-Exclude Versions

Using **x-av-versions** an Operation can be defined as available **from** version  **to** version, and can **exclude** some versions if needed:

```yaml
paths:
  /speakers:
    post:
      tags:
        - Speaker
      summary: Create Speaker
      operationId: createSpeaker
      x-av-versions:
        from: 2.0
        to: 10.0
        exclude: [5.0, 6.0]
      requestBody:
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/Speaker'
      responses:
        '200':
          description: OK
          content:
            'application/json':
              schema:
                $ref: '#/components/schemas/Speaker'
        '400':
          description: Invalid input
```

Everything which is not marked with **x-av-versions** will be kept available from the default version - **info.version**.

### Override initially declared Operation properties

Another way of using **x-av-versions** on operation level is to override path operation properties definition (there are some which are not supported [Check Limitations](#limitations)) in next versions.

For example let's say that we want to change the response code from '200' to '201' from version 3.0 on. We need just to add the **definitions** field and use it like this:

```yaml
paths:
  /speakers:
    post:
      tags:
        - Speaker
      summary: Create Speaker
      operationId: createSpeaker
      x-av-versions:
        from: 2.0
        to: 10.0
        definitions:
          3.0:
            responses:
              '201':
                 description: OK
                 content:
                   'application/json':
                 schema:
                    $ref: '#/components/schemas/Speaker'
              '400':
                description: Invalid input
          exclude: [5.0, 6.0]
      requestBody:
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/Speaker'
      responses:
        '200':
          description: OK
          content:
            'application/json':
              schema:
                $ref: '#/components/schemas/Speaker'
        '400':
          description: Invalid input
```

In that example all the "default" (which are not part of **x-av-versions** definition) properties will be valid for version 2.0 and from version 3.0 on, responses will be taken from **x-av-definitions**.

### Parameters

Spring AV can be used to define parameters across versions as well, the same rules **from-to-exclude** are applicable here.

It is possible to override a parameter defined on Path level and to remove it in a particular operation, something which is not possible in the current API spec.

```yaml
parameters:
  - in: query
    name: offset
    x-av-versions:
      from: 2.0
      to: 8.0
    schema:
      type: integer
    description: The number of items to skip before starting to collect the result set
  - in: query
    name: limit
    schema:
      type: integer
    description: The numbers of items to return
```

If there is a need for value changes there is a way to override the "default" definition using the **definitions** object so in this way the new Parameters set will be applicable since the mapped version as a key.

```yaml
paths:
  /speakers:
    post:
      requestBody:
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/Speaker'
      responses:
        '200':
          description: OK
          content:
            'application/json':
              schema:
                $ref: '#/components/schemas/Speaker'
        '400':
          description: Invalid input
      parameters:
        - in: query
          name: offset
          x-av-versions:
            from: 2.0
            to: 8.0
          schema:
            type: integer
          description: The number of items to skip before starting to collect the result set
        - in: query
          name: limit
          schema:
            type: integer
          description: The numbers of items to return
      x-av-versions:
        from: 2
        to: 10
        exclude: [5.0, 6.0]
        definitions:
          4.0:
            parameters:
              - in: query
                name: offset
                schema:
                  type: integer
                description: The number of items to skip before starting to collect the result set
```


### Components schemas
We can use **x-av-versions** on schema level, so we can define on which versions particular field is available using **from-to-exclude**.

```yaml
components:
  schemas:
    Speaker:
      type: object
      properties:
        firstName:
          type: string
          description: First name of the speaker
          example: Dan
        lastName:
          type: string
          description: Last name of the speaker
          example: Kolov
        email:
          x-av-versions:
            from: 2.0
            to: 10.0
            exclude: [5.0, 6.0]
            required:
              from: 4.0
              to: 8.0
              exclude: [7.0, 8.0]
          type: string
          description: Email of the speaker
          example: dan.kolov@gmail.com
        title:
          type: string
          description: Speaker's title in the company
          example: Agile coach
        yearsInEndava:
          type: integer
          description: The number of years the speaker has worked for Endava
          example: 100
```
### Components requestBodies
**x-av-versions.definitions** can be used to change request properties in across versions, for example:

```yaml
components:
  requestBodies:
    SpeakerRequest:
      x-av-versions:
        definitions:
          3.0:
            description: Request description from definition 3
            required: true
          2.0:
            description: Request description from definition 2
      description: Default description
      content:
        application/json:
          schema:
            type: object
            properties:
              defaultProperty:
                type: string
```
### Components responses
In a similar manner, we can use **x-av-versions.definitions** to add versioning for responses.

```yaml
components:
  responses:
    NotFound:
      x-av-versions:
        definitions:
          "3.0":
            description: from definition 3
            content:
              application/json:
                schema:
                  type: object
                  properties:
                    codeFromDefinitionThree:
                      type: string
                    messageFromDefinitionThree:
                      type: string
          "2.0":
            content:
              application/json:
                schema:
                  $ref: '#/components/schemas/SpeakerNew'
      description: Unauthorized
      content:
        application/json:
          schema:
            type: object
            properties:
              codeDefault:
                type: string
              messageDefault:
                type: string
```

### Required Request properties

Using AV we can declare **required** properties across the versions again by using **from-to-exclude** like:

```yaml
components:
  schemas:
    Speaker:
      type: object
      properties:
        firstName:
          type: string
          description: First name of the speaker
          example: Dan
        lastName:
          x-av-versions:
            required:
              from: 2
          type: string
          description: Last name of the speaker
          example: Kolov
        email:
          x-av-versions:
            required:
              from: 2
              to: 3
          type: string
          description: Email of the speaker
          example: dan.kolov@gmail.com
        title:
          x-av-versions:
            required:
              from: 2
              to: 4
              exclude: [3]
          type: string
          description: Speaker's title in the company
          example: Agile coach
        yearsInCompany:
          x-av-versions:
            required:
              exclude: [3]
          type: integer
          description: The number of years the speaker has worked for Endava
          example: 100
```
or we can override already defined fields as **required** in the fallowing versions like:

```yaml
components:
  schemas:
    Speaker:
      type: object
      required:
        - firstName
        - email
      properties:
        firstName:
          type: string
          description: First name of the speaker
          example: Dan
          x-av-versions:
            required:
              to: 1
        lastName:
          x-av-versions:
            required:
              from: 2
          type: string
          description: Last name of the speaker
          example: John
        email:
          x-av-versions:
            required:
              to: 2
          type: string
          description: Email of the speaker
          example: john.doe@gmail.com
        title:
          x-av-versions:
            required:
              from: 2
              to: 4
              exclude: [3]
          type: string
          description: Speaker's title in the company
          example: Agile coach
        yearsInCompany:
          x-av-versions:
            required:
              exclude: [3]
          type: integer
          description: The number of years the speaker has worked for Endava
          example: 100
```

## Limitations
### Numeric Versions Only

The Spring AV has a main goal to provide simple and maintainable way to add version support using the URI Path concept.

Versions must be Numeric only. AV cannot support text base paths because it does compare **from** to be less than **to**, so it can stop adding a path or a property after certain version. This applies for the default file version which is a String type, must be a Numeric value:

```yaml
openapi: "3.0.2"
info:
  title: Example project
  version: "1.0"
```
### Versioned files are in a same package

The generated API and Controllers for all the supported versions in a YAML file are in the same package, as package per version path operation is not possible to be defined. This is a limitation from the Default Code Generator. The same is valid for the Model files.

### No logical validation

No logical validation is available based on the  **x-av-versions** properties, so such type of configuration is possible:

```yaml
x-av-versions:
  from: 3.0
  to: 2.0
```

This is a valid configuration, but basically this Path or Model will not be present anywhere. So the correct logical usage is responsibility of Spring AV users.

### Components objects supported: Schemas, Request Bodies and Responses

Currently, **x-av-versions** can be used inside **Components.Schema**,  **Components.RequestBodies**,  **Components.Responses** objects.
