# Hazelcast Spring Session

## Spring Session
Spring Session provides an API and implementations for managing a user's session information, while also making it trivial to support clustered sessions without being tied to an application container specific solution.
It also provides transparent integration with:

* `HttpSession` - allows replacing the `HttpSession` in an application container (i.e. Tomcat) neutral way, with support for providing session IDs in headers to work with RESTful APIs.
* `WebSocket` - provides the ability to keep the `HttpSession` alive when receiving WebSocket messages
* `WebSession` - allows replacing the Spring WebFlux's `WebSession` in an application container neutral way.

## This module 

Hazelcast Spring Session uses [Hazelcast Platform](https://github.com/hazelcast/hazelcast) to store user session information in a cluster. The SessionRepository uses Hazelcast's IMap to store the session information, giving users [AP characteristics](https://www.designgurus.io/answers/detail/what-is-the-cap-theorem).

## Getting Started

We recommend you visit the [Hazelcast Documentation site](https://docs.hazelcast.com/) and check out provided tutorials and [Hazelcast Code Samples](https://github.com/hazelcast/hazelcast-code-samples) for additional code samples (TBD).

### Basic usage
To start using `hazelcast-spring-session`, you need to add following dependency:
```xml
<dependency>
    <groupId>com.hazelcast</groupId>
    <artifactId>hazelcast-spring-session</artifactId>
    <version>4.0.0-SNAPSHOT</version> <!-- note: SNAPSHOT is used here only before first major release -->
</dependency>
```
or using Gradle:
```kotlin
// note: SNAPSHOT is used here only before first major release
implementation("com.hazelcast:hazelcast-spring-session:4.0.0-SNAPSHOT")
```

Then you need to add `@EnableHazelcastHttpSession` annotation to your `@Configuration` class.

Have in mind, that this module's classes must be present on the classpath of all members of the cluster. In client-server architecture you can either put project's main artifact in the `lib` directory of Hazelcast distribution or use tool like [User Code Namespaces](https://docs.hazelcast.com/hazelcast/latest/clusters/user-code-namespaces) to upload the code.

### Configuring Hazelcast instance
To use the project, your Hazelcast instances must be configured correctly. An example configuration is below:

```java
import com.hazelcast.spring.session.HazelcastIndexedSessionRepository;
import com.hazelcast.spring.session.PrincipalNameExtractor;
import com.hazelcast.spring.session.HazelcastSessionSerializer;
import com.hazelcast.spring.session.HazelcastSessionSerializer;
import org.springframework.session.MapSession;
import com.hazelcast.config.Config;
import com.hazelcast.config.AttributeConfig;
import com.hazelcast.config.SerializerConfig;

```

```java
// 
Config config = new Config();
// add attribute configuration, so principal name can be retrieved more efficiently
AttributeConfig attributeConfig = new AttributeConfig()
        .setName(HazelcastIndexedSessionRepository.PRINCIPAL_NAME_ATTRIBUTE)
        .setExtractorClassName(PrincipalNameExtractor.class.getName());

// configuration for the IMap storing the user session data
config.getMapConfig(HazelcastIndexedSessionRepository.DEFAULT_SESSION_MAP_NAME)
    .addAttributeConfig(attributeConfig)
    // we are adding an index on principal name for faster querying
    .addIndexConfig(
            new IndexConfig(IndexType.HASH, HazelcastIndexedSessionRepository.PRINCIPAL_NAME_ATTRIBUTE));
// serialization configuration - session objects must be sent between members..
SerializerConfig serializerConfig = new SerializerConfig();
serializerConfig.setImplementation(new HazelcastSessionSerializer()).setTypeClass(MapSession.class);
config.getSerializationConfig().addSerializerConfig(serializerConfig);
```

## Building from Source

Hazelcast Spring Session uses a [Gradle](https://gradle.org-based) build system.
In the instructions below, `./gradlew` is invoked from the root of the source tree and serves as
a cross-platform, self-contained bootstrap mechanism for the build.

Check out sources:

```bash
git clone git@github.com:hazelcast/hazelcast-spring-session.git
```

Install jars into your local Maven cache:

```bash
./gradlew install
```

Compile and test; build all jars:

```bash
./gradlew build
```

## Documentation

You can find the documentation, samples, and guides for using Spring Session on the [Hazelcast Documentation page](https://docs.hazelcast.com/hazelcast/latest/spring/overview).

## License

Hazelcast Spring Session is Open Source software released under the [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0.html).
