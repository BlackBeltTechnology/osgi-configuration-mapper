package hu.blackbelt.configuration.mapper;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name="Solr container manager")
@interface DefaultTemplatedConfigSetConfig {

    @AttributeDefinition(
            name = "Template path",
            description = "The template pathes monitored inside bundles"
    )
    String templatePath();

    @AttributeDefinition(
            name = "Environment prefix",
            description = "Environment prefix used to get environment variables. For example: When X_ prefix used X_PART1_PART2 env variabsle used " +
                    "as part1Part2 context variable in templates."
    )
    String envPrefix();
}
