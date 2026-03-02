# Contributing to OSGi Configuration Mapper

## Prerequisites

Your development environment must meet the requirements described in the parent project's [CONTRIBUTING guide](https://github.com/BlackBeltTechnology/judo-community/blob/develop/CONTRIBUTING.adoc). In particular, ensure you have:

- **Java 21** JDK
- **Maven 3.9.4+** (or use the included `./mvnw` wrapper)

## Project Structure

This is a single-module Maven project that produces an OSGi bundle (`bundle` packaging). The main source code lives in the `hu.blackbelt.configuration.mapper` package.

```mermaid
graph TD
    subgraph "Source Layout"
        Main["src/main/java<br/>hu.blackbelt.configuration.mapper"]
        Resources["src/main/resources<br/>configuration_mapper_v1.xsd"]
        Bindings["src/main/bindings<br/>JAXB binding customizations"]
        Test["src/test/java<br/>PAX Exam integration tests"]
        TestRes["src/test/resources<br/>config-templates/ test fixtures"]
    end

    Main --> Resources
    Main --> Bindings
    Test --> TestRes
```

## Build Commands

Run tests only:

```sh
mvn clean test
```

Full build (compile, test, package, install to local repo):

```sh
mvn clean install
```

Run a single test class:

```sh
mvn test -Dtest=DefaultTemplatedConfigSetTest
```

## Build Lifecycle

```mermaid
flowchart LR
    clean[clean] --> gen["generate-sources<br/>(JAXB xjc, delombok)"]
    gen --> compile[compile]
    compile --> test["test<br/>(PAX Exam + Felix)"]
    test --> package["package<br/>(OSGi bundle)"]
    package --> verify["verify<br/>(JaCoCo coverage)"]
    verify --> install[install]
    install --> deploy[deploy]
    package -.->|"profile: sign-artifacts"| sign[sign artifacts]
```

## Submitting an Issue

Before filing a new issue, search the [issue tracker](https://github.com/BlackBeltTechnology/osgi-configuration-mapper/issues) — your problem may already have been reported or resolved.

When reporting a bug, include:

- Output of `java -version` and `mvn -version`
- The `pom.xml` or `.flattened-pom.xml` if relevant
- A minimal reproducible scenario — this is essential for maintainers to confirm and fix the bug efficiently

File new issues using the [issue form](https://github.com/BlackBeltTechnology/osgi-configuration-mapper/issues/new/choose).

## Submitting a Pull Request

This project follows [GitHub's standard forking model](https://guides.github.com/activities/forking/). Fork the repository, make your changes, and submit a pull request.
