# OSGi Configuration Mapper - Project Documentation

## Project Overview

**Repository:** BlackBeltTechnology/osgi-configuration-mapper
**License:** Apache License 2.0
**Java Version:** 21
**Build System:** Maven 3.9.4 with Maven Wrapper (`./mvnw`)

1. Provides a centralized OSGi configuration management system using Freemarker templates
2. Monitors OSGi bundles at runtime for `.template` files and creates/updates configurations via `ConfigurationAdmin`
3. Supports three variable scopes (OSGi properties, environment variables, JVM system properties) with configurable precedence
4. Handles factory PID configurations and conditional component instantiation through XML extension files
5. Tracks configuration changes using SHA1 checksums to avoid unnecessary updates

## Directory Structure

```
osgi-configuration-mapper/
├── src/
│   ├── main/
│   │   ├── java/hu/blackbelt/configuration/mapper/   # All source classes
│   │   ├── resources/configuration_mapper_v1.xsd      # XML schema for extensions
│   │   └── bindings/SkipElementProperties.xjc         # JAXB binding customizations
│   └── test/
│       ├── java/hu/blackbelt/configuration/mapper/    # PAX Exam integration tests
│       └── resources/config-templates/                # Test template fixtures
├── .github/workflows/                                 # CI/CD pipelines
├── .mvn/                                              # Maven wrapper config
├── pom.xml                                            # Build configuration
├── README.md                                          # User documentation
├── CONTRIBUTING.md                                    # Contributor guide
└── LICENSE.txt                                        # Apache 2.0 license
```

## Core Modules

This is a single-module project (packaging: `bundle`). All classes live in `hu.blackbelt.configuration.mapper`.

### OSGi Component Layer

| Class | Type | Purpose |
|-------|------|---------|
| `DefaultTemplatedConfigSet` | DS Component | Entry point. Activates on `configset-*.cfg`, wires ConfigurationAdmin, creates handler and tracker |
| `DefaultTemplatedConfigSetConfig` | @ObjectClassDefinition | OSGi MetaType config interface: `templatePath`, `envPrefix`, `variableScopePrecedence` |

### Core Logic Layer

| Class | Type | Purpose |
|-------|------|---------|
| `OsgiTemplatedConfigurationSetHandler` | Service | Processes templates, manages OSGi configurations via ConfigurationAdmin, SHA1 checksum tracking |
| `TemplateProcessor` | Service | Freemarker template engine, variable scope merging, conditional evaluation, PID resolution |
| `TemplateResourceBundleTracker` | BundleTracker | Monitors active bundles for template resources, triggers config processing on bundle changes |
| `ExtensibleBundleTracker<T>` | BundleTracker | Generic async bundle tracker with single-threaded executor to prevent platform thread leaks |

### Data Model

| Class | Type | Purpose |
|-------|------|---------|
| `ConfigurationEntry` | @Builder POJO | Represents a template + optional XML spec + optional instance name |
| `ConfigState` | Enum | Configuration states: `UNCHANGED`, `NEW`, `FOREIGN`, `CHECKSUMCHANGE` |
| `Utils` | Utility | SHA1 hashing, Dictionary-to-Map conversion, properties loading, PID parsing |

### Generated Code

| Source | Generator | Purpose |
|--------|-----------|---------|
| `configuration_mapper_v1.xsd` | JAXB (jaxb2-maven-plugin) | Generates `Components` and `ComponentType` classes for XML extension parsing |

## Technology Stack

### Core Technologies
- **OSGi Core 6.0.0** and **OSGi Compendium 6.0.0** — component model, ConfigurationAdmin, BundleTracker
- **OSGi Declarative Services** — @Component, @Activate, @Modified, @Deactivate, @Reference
- **OSGi MetaType** — @ObjectClassDefinition, @AttributeDefinition for config UI
- **Freemarker 2.3.28** — template processing engine
- **JAXB API 2.2** — XML extension file parsing (with Glassfish XJC 2.3.1 for code generation)
- **Google Guava 30.0-jre** — ImmutableMap, CaseFormat, Ordering, collection utilities
- **Lombok 1.18.34** — @Slf4j, @SneakyThrows, @Builder, @Getter

### Build & Quality
- **Maven 3.9.4** with Maven Wrapper
- **maven-bundle-plugin 6.0.0** (Felix) — OSGi bundle packaging and manifest generation
- **jaxb2-maven-plugin 3.1.0** — XSD-to-Java code generation
- **flatten-maven-plugin 1.2.7** — CI-friendly `${revision}` versioning
- **JaCoCo 0.8.12** — code coverage
- **SonarQube Maven Plugin 3.9.1.2184** — static analysis
- **JUnit Jupiter 5.6.2** — test framework
- **PAX Exam 4.13.4** — OSGi integration testing with Apache Felix 5.6.12
- **Mockito 3.0.0** — mocking
- **Hamcrest 1.3** — assertion matchers

## Build Commands

```bash
# Full build (compile, test, package as OSGi bundle, install to local repo)
mvn clean install

# Run tests only
mvn clean test

# Run a single test class
mvn test -Dtest=DefaultTemplatedConfigSetTest

# Skip tests
mvn clean install -DskipTests

# Build with artifact signing
mvn clean install -Psign-artifacts

# Deploy to Nexus (judong)
mvn clean deploy -Prelease-judong

# Deploy to Maven Central
mvn clean deploy -Prelease-central
```

> **Note:** The Maven wrapper (`./mvnw`) is available and configured with JVM options: `-Xms1024m -Xmx2048m -Dfile.encoding=UTF-8`.

### Maven Profiles

| Profile | Purpose |
|---------|---------|
| `modules` | Default active profile (controlled by `skipModules` property) |
| `sign-artifacts` | GPG-sign artifacts for release (uses `sign-maven-plugin`) |
| `release-dummy` | Deploy to local `/tmp/` filesystem for testing |
| `release-judong` | Deploy to Judong Nexus repository |
| `release-central` | Deploy to Maven Central via Sonatype OSSRH |
| `generate-github-asciidoc-diagrams` | Generate diagram images from AsciiDoc files |
| `update-source-code-license` | Update Apache 2.0 license headers on all source files |

## Key Configuration Files

| File | Purpose |
|------|---------|
| `pom.xml` | Build config, dependencies, profiles, plugin management |
| `src/main/resources/configuration_mapper_v1.xsd` | XML schema for extension files (factory PIDs, conditions) |
| `src/main/bindings/SkipElementProperties.xjc` | JAXB binding customization for XSD code generation |
| `.mvn/jvm.config` | JVM options for Maven build (`-Xms1024m -Xmx2048m`) |
| `.mvn/extensions.xml` | Maven build extensions (Tycho Pomless, Maven Wagon) |
| `.github/workflows/build.yml` | Main CI/CD pipeline |

## Development Environment

**Required:**
- Java 21 JDK
- Maven 3.9.4+ (or use `./mvnw`)

**Recommended IDE settings:**
- Disable Java auto-formatting (project does not enforce a formatter)
- Disable auto-organize imports
- Enable Maven source download
- Exclude `target/` from indexing

## Git Workflow

- **Main Branch:** `develop`
- **Release Branch:** `master` (latest released version)
- **Versioning:** CI-friendly `${revision}` property, currently `1.0.1-SNAPSHOT`
- **Branch naming:** `feature/JNG-xxx_description`, `bugfix/JNG-xxx_description`, `release/X.Y.Z`
- **Commit rule:** Every commit must reference a JIRA ticket (`JNG-xxx`)
- See [CIFLOW.md](.github/CIFLOW.md) for detailed workflow diagrams

## Important Notes

1. This project produces an OSGi **bundle** (not a regular JAR). The `maven-bundle-plugin` generates the OSGi manifest with proper `Import-Package` and `Export-Package` headers.
2. JAXB classes are **generated** during `generate-sources` phase from `configuration_mapper_v1.xsd`. Do not edit files in `target/generated-sources/jaxb/`.
3. Lombok's `delombok` runs during `generate-sources` to produce de-sugared source for Javadoc generation.
4. Tests are **OSGi integration tests** using PAX Exam — they boot a real Felix OSGi container. They require `--add-opens` JVM flags (configured in surefire plugin).
5. Sensitive configuration values (keys containing "password" or "secret") are automatically masked in log output.
6. The `ExtensibleBundleTracker` uses a single-threaded executor to avoid leaking platform threads during bundle event processing.

## Related Documentation

- [README.md](README.md) — User-facing documentation with architecture diagrams
- [CONTRIBUTING.md](CONTRIBUTING.md) — Contributor guide and build instructions
- [.github/CIFLOW.md](.github/CIFLOW.md) — CI/CD workflow and branching strategy
- [LICENSE.txt](LICENSE.txt) — Apache License 2.0
