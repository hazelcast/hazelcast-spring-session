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
