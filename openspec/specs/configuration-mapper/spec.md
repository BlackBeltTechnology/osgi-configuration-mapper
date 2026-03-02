# configuration-mapper Specification

## Purpose

Provides centralized OSGi configuration management by processing Freemarker templates found in OSGi bundles, substituting variables from a configset file, and creating/updating OSGi configurations via ConfigurationAdmin.

## Architecture

The module is a single OSGi bundle with these key components:

- **DefaultTemplatedConfigSet** — OSGi DS component (immediate, `ConfigurationPolicy.REQUIRE`) that activates on `configset-*.cfg` deployment and orchestrates the handler and bundle tracker.
- **OsgiTemplatedConfigurationSetHandler** — Core logic for processing template entries, managing configuration lifecycle through ConfigurationAdmin, and tracking state via SHA1 checksums.
- **TemplateProcessor** — Freemarker-based template engine that merges variables from three scopes (osgi, environment, system) with configurable precedence, resolves PIDs, and evaluates conditions.
- **TemplateResourceBundleTracker** — Monitors active OSGi bundles for `.template` files in a configurable path and triggers configuration processing on bundle lifecycle events.
- **ConfigurationEntry** — Data model representing a template URL, optional XML spec URL, and optional instance name.
- **ExtensibleBundleTracker** — Generic async bundle tracker using a single-threaded executor to prevent platform thread leaks.

## Requirements

### Requirement: ConfigSet activation on configuration presence

The `DefaultTemplatedConfigSet` component SHALL activate only when an OSGi configuration with the `configset` PID is present (`ConfigurationPolicy.REQUIRE`).

#### Scenario: ConfigSet deployed
- **GIVEN** an OSGi runtime with ConfigurationAdmin available
- **WHEN** a `configset-*.cfg` file is deployed
- **THEN** the `DefaultTemplatedConfigSet` component activates with the configuration properties
- **THEN** an `OsgiTemplatedConfigurationSetHandler` is created with the config properties
- **THEN** a `TemplateResourceBundleTracker` begins scanning active bundles

#### Scenario: ConfigSet updated
- **GIVEN** an active `DefaultTemplatedConfigSet` component
- **WHEN** the configset properties are modified
- **THEN** the handler's OSGi configs are updated via `updateOsgiConfigs()`
- **THEN** all tracked bundles are refreshed via `refreshAllBundles()`

#### Scenario: ConfigSet removed
- **GIVEN** an active `DefaultTemplatedConfigSet` component
- **WHEN** the configset configuration is removed
- **THEN** the bundle tracker is destroyed
- **THEN** all configurations created by this configset are deleted from ConfigurationAdmin

### Requirement: Template discovery from OSGi bundles

The `TemplateResourceBundleTracker` SHALL discover `.template` files from the configured `templatePath` directory inside active OSGi bundles.

#### Scenario: Bundle with templates becomes active
- **GIVEN** a configset is active with `templatePath=/config-templates`
- **WHEN** a bundle containing `/config-templates/my.service.template` becomes ACTIVE
- **THEN** a `ConfigurationEntry` is created for the template
- **THEN** `processConfigs()` is invoked with all known entries across all tracked bundles

#### Scenario: Bundle with templates is removed
- **GIVEN** a tracked bundle with template entries
- **WHEN** the bundle is removed from the OSGi runtime
- **THEN** its entries are removed from the tracker
- **THEN** `processConfigs()` is re-invoked to reconcile (orphaned configs are deleted)

#### Scenario: Bundle modified with unchanged templates
- **GIVEN** a tracked bundle with template entries
- **WHEN** the bundle is modified but template checksums are unchanged
- **THEN** no configuration processing occurs

### Requirement: Variable scope merging with configurable precedence

The `TemplateProcessor` SHALL merge variables from osgi, environment, and system scopes according to the `variableScopePrecedence` configuration.

#### Scenario: Default precedence (osgi, environment, system)
- **GIVEN** `variableScopePrecedence=osgi,environment,system`
- **GIVEN** osgi config has `dbUrl=osgi-value`
- **GIVEN** system property has `dbUrl=system-value`
- **WHEN** a template referencing `${dbUrl}` is processed
- **THEN** the resolved value is `system-value` (system scope has highest precedence, listed last)

#### Scenario: Direct scope access
- **GIVEN** any variable scope precedence setting
- **WHEN** a template references `${system.java_home}`
- **THEN** the JVM system property `java.home` is returned (dots replaced with underscores)
- **WHEN** a template references `${environment.PATH}`
- **THEN** the OS environment variable `PATH` is returned

### Requirement: Environment variable prefix mapping

The `TemplateProcessor` SHALL transform prefixed environment variables from `UPPER_SNAKE_CASE` to `lowerCamelCase` after stripping the prefix.

#### Scenario: Prefixed environment variable mapping
- **GIVEN** `envPrefix=X_`
- **GIVEN** environment variable `X_DATABASE_URL=jdbc:postgresql://localhost/db`
- **WHEN** a template referencing `${databaseUrl}` is processed
- **THEN** the resolved value is `jdbc:postgresql://localhost/db`

### Requirement: Dot-to-underscore conversion in variable names

The `TemplateProcessor` SHALL replace all dots (`.`) with underscores (`_`) in variable names from all scopes.

#### Scenario: OSGi property with dots
- **GIVEN** an OSGi config property `my.database.url=value`
- **WHEN** the template properties are built
- **THEN** the variable is accessible as `${my_database_url}`

### Requirement: PID-based configuration creation

The `OsgiTemplatedConfigurationSetHandler` SHALL create OSGi configurations with PIDs derived from template file names.

#### Scenario: Simple PID template
- **GIVEN** a template file `com.example.service.template`
- **GIVEN** no corresponding XML spec file
- **WHEN** the template is processed
- **THEN** a configuration with PID `com.example.service` is created via ConfigurationAdmin

#### Scenario: Factory PID template
- **GIVEN** a template file `com.example.service-instance1.template`
- **GIVEN** no corresponding XML spec file
- **WHEN** the template is processed
- **THEN** a factory configuration with factory PID `com.example.service` is created for instance `instance1`

### Requirement: XML extension file support

The handler SHALL support optional XML extension files (following `configuration_mapper_v1.xsd`) to control factory PIDs and conditional instantiation.

#### Scenario: Conditional component creation
- **GIVEN** a template `my.service.template` and XML file `my.service.xml`
- **GIVEN** the XML contains `<condition>enableFeature?? &amp;&amp; enableFeature == "true"</condition>`
- **GIVEN** the configset has `enableFeature=true`
- **WHEN** the template is processed
- **THEN** the configuration is created (condition evaluates to true)

#### Scenario: Conditional component skipped
- **GIVEN** the same setup but `enableFeature=false`
- **WHEN** the template is processed
- **THEN** no configuration is created (condition evaluates to false)

#### Scenario: Expression factory PID
- **GIVEN** an XML file with `<factoryPid>${serviceName}</factoryPid>` (contains `$`)
- **WHEN** the template is processed
- **THEN** the factory PID is resolved by evaluating the Freemarker expression

### Requirement: SHA1 checksum-based change detection

The handler SHALL use SHA1 checksums to track configuration state and avoid unnecessary updates.

#### Scenario: Unchanged configuration
- **GIVEN** an existing configuration with a stored checksum
- **WHEN** the template is re-processed and produces the same output
- **THEN** the configuration is NOT updated (state: `UNCHANGED`)

#### Scenario: Changed configuration
- **GIVEN** an existing configuration with a stored checksum
- **WHEN** the template is re-processed and produces different output
- **THEN** the configuration is updated with new properties and checksum (state: `CHECKSUMCHANGE`)

#### Scenario: Foreign configuration
- **GIVEN** a configuration exists but was not created by this configset (missing tracking properties)
- **WHEN** the template is processed
- **THEN** the foreign configuration is deleted and recreated (state: `FOREIGN`)

### Requirement: Orphaned configuration cleanup

The handler SHALL delete configurations that are no longer represented by any template entry.

#### Scenario: Template removed from bundle
- **GIVEN** a configuration was previously created from a template
- **WHEN** `processConfigs()` runs and the template is no longer present
- **THEN** the orphaned configuration is deleted from ConfigurationAdmin

### Requirement: Sensitive value masking in logs

The handler and template processor SHALL mask values of properties whose keys contain "password" or "secret" in log output.

#### Scenario: Password property logged
- **GIVEN** a configuration property `database.password=s3cret`
- **WHEN** the configuration is logged
- **THEN** the logged value shows `database.password = **************`
