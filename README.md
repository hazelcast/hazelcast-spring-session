= Hazelcast Spring Session

== Spring Session
Spring Session provides an API and implementations for managing a user's session information, while also making it trivial to support clustered sessions without being tied to an application container specific solution.
It also provides transparent integration with:

* `HttpSession` - allows replacing the `HttpSession` in an application container (i.e. Tomcat) neutral way, with support for providing session IDs in headers to work with RESTful APIs.
* `WebSocket` - provides the ability to keep the `HttpSession` alive when receiving WebSocket messages
* `WebSession` - allows replacing the Spring WebFlux's `WebSession` in an application container neutral way.

== Modules

Hazelcast Spring Session uses https://github.com/hazelcast/hazelcast[Hazelcast Platform] to store user's session information in a cluster, The SessionRepository uses Hazelcast's IMap to store the session information, giving users https://www.designgurus.io/answers/detail/what-is-the-cap-theorem[AP characteristics].

== Getting Started

We recommend you visit the https://docs.hazelcast.com/[Hazelcast Documentation site] and check out provided tutorials and https://github.com/hazelcast/hazelcast-code-samples[Hazelcast Code Samples] for additional code samples (TBD).

== Building from Source

Hazelcasst Spring Session uses a https://gradle.org[Gradle]-based build system.
In the instructions below, `./gradlew` is invoked from the root of the source tree and serves as
a cross-platform, self-contained bootstrap mechanism for the build.

Check out sources
----
git clone git@github.com:hazelcast/hazelcast-spring-session.git
----

Install jars into your local Maven cache
----
./gradlew install
----

Compile and test; build all jars
----
./gradlew build
----


== Documentation

You can find the documentation, samples, and guides for using Spring Session on the https://docs.hazelcast.com/home/[Hazelcast Docmentation page].

== License

Hazelcast Spring Session is Open Source software released under the https://www.apache.org/licenses/LICENSE-2.0.html[Apache 2.0 license].