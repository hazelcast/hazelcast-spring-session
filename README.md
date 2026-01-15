# Hazelcast Spring Session

Spring Session provides an API and implementations for managing a user's session information, while also making it trivial to support clustered sessions without being tied to an application container specific solution.
It also provides transparent integration with:

* `HttpSession` - allows replacing the `HttpSession` in an application container (i.e. Tomcat) neutral way, with support for providing session IDs in headers to work with RESTful APIs.
* `WebSocket` - provides the ability to keep the `HttpSession` alive when receiving WebSocket messages
* `WebSession` - allows replacing the Spring WebFlux's `WebSession` in an application container neutral way.

Hazelcast Spring Session uses [Hazelcast Platform](https://github.com/hazelcast/hazelcast) to store user session information in a cluster. The SessionRepository uses Hazelcast's IMap to store the session information, giving users [AP characteristics](https://www.designgurus.io/answers/detail/what-is-the-cap-theorem).

## Getting started

We recommend you visit the [Hazelcast Documentation site](https://docs.hazelcast.com/) and [Hazelcast Code Samples](https://github.com/hazelcast/hazelcast-code-samples) for additional code samples.

### Basic usage

To start using `hazelcast-spring-session`, you need to add following dependency:

```xml
<dependency>
    <groupId>com.hazelcast</groupId>
    <artifactId>hazelcast-spring-session</artifactId>
    <version>4.0.0-RC1</version>
</dependency>
```
or using Gradle:

```kotlin
// note: SNAPSHOT is used here only before first major release
implementation("com.hazelcast:hazelcast-spring-session:4.0.0-RC1")
```

Then you need to add `@EnableHazelcastHttpSession` annotation to your `@Configuration` class.

Note that this module's classes must be present on the classpath of all members of the Hazelcast cluster. In a client-server architecture you can either put your project's main artifact in the `lib` directory of the Hazelcast distribution or use a feature like [User Code Namespaces](https://docs.hazelcast.com/hazelcast/latest/clusters/user-code-namespaces) to upload the code.

### Configuring a Hazelcast instance

To use the project, your Hazelcast instances must be configured correctly. An example configuration is below:

```java
import com.hazelcast.spring.session.HazelcastIndexedSessionRepository;
import com.hazelcast.spring.session.HazelcastSessionConfiguration;
import com.hazelcast.config.Config;
import com.hazelcast.config.IndexConfig;
```

```java
// 
Config config = new Config();

// configuration for the IMap storing the user session data
config.getMapConfig(HazelcastIndexedSessionRepository.DEFAULT_SESSION_MAP_NAME)
    // we are adding an index on principal name for faster querying
    .addIndexConfig(
            new IndexConfig(IndexType.HASH, HazelcastIndexedSessionRepository.PRINCIPAL_NAME_ATTRIBUTE));

// serialization configuration - session objects must be sent between members
HazelcastSessionConfiguration.applySerializationConfig(config);
```

## Building from source

Hazelcast Spring Session uses a [Gradle-based](https://gradle.org) build system.

In the instructions below, `./gradlew` is invoked from the root of the source tree and serves as
a cross-platform, self-contained bootstrap mechanism for the build.

Check out sources:

```bash
git clone git@github.com:hazelcast/hazelcast-spring-session.git
```

Install jars into your local Maven cache:

```bash
./gradlew build publishToMavenLocal
```

Compile and test; build all jars:

```bash
./gradlew build
```

## Documentation

You can find more information about using Spring Session in the [Hazelcast documentation](https://docs.hazelcast.com/hazelcast/latest/spring/overview).

## License

Hazelcast Spring Session is Open Source software released under the [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0.html).

## Migration from `Spring Session Hazelcast` 3.x

When migrating from [Spring Session](https://github.com/spring-projects/spring-session)'s Hazelcast module, which was under Spring Team ownership until version 4.0, you need to make some adjustments:
1. GroupId is changed to `com.hazelcast.spring` and artifactId is now `hazelcast-spring-session`.
2. All Hazelcast-specific classes were moved from `org.springframework.session.hazelcast` to `com.hazelcast.spring.session`.
3. Remove this configuration for PrincipalNameExtractor.
4. Change serialization configuration. Replace previous SerializationConfig with the usage of `HazelcastSessionConfiguration.applySerializationConfig(config);`.

Please note, that if you want to run Hazelcast Spring Session with Hazelcast 5.6 and Spring Boot 4, you need to add to your configuration class following exclusion:
```java
@EnableAutoConfiguration(excludeName = "com.hazelcast.spring.HazelcastObjectExtractionConfiguration")
```

For more details, please see https://docs.hazelcast.com/hazelcast/5.7-snapshot/spring/hazelcast-spring-session#migrate-from-spring-session-hazelcast-3-x.