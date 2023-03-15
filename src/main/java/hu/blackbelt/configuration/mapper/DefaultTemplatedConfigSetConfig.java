package hu.blackbelt.configuration.mapper;

/*-
 * #%L
 * OSGi Configuration mapper
 * %%
 * Copyright (C) 2018 - 2023 BlackBelt Technology
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name="Templated configuration set manager")
@interface DefaultTemplatedConfigSetConfig {

    @AttributeDefinition(
            name = "Template path",
            description = "The template pathes monitored inside bundles"
    )
    String templatePath() default "/config-templates";

    @AttributeDefinition(
            name = "Environment prefix",
            description = "Environment prefix used to get environment variables. For example: When X_ prefix used X_PART1_PART2 env variabsle used " +
                    "as part1Part2 context variable in templates."
    )
    String envPrefix();

    @AttributeDefinition(
            name = "Variable scope precedence",
            description = "Comma-separated list of variable scope precedences (first: lowest, last: highest)."
    )
    String variableScopePrecedence() default "osgi,environment,system";
}
