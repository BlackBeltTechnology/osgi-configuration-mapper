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

import lombok.extern.slf4j.Slf4j;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.Designate;

import java.util.*;

@Component(name = "configset", immediate = true, configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = DefaultTemplatedConfigSetConfig.class)
@Slf4j
public class DefaultTemplatedConfigSet {

    @Reference(cardinality = ReferenceCardinality.MANDATORY, policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY)
    ConfigurationAdmin configurationAdmin;

    private OsgiTemplatedConfigurationSetHandler osgiTemplatedConfigurationSetHandler;
    private TemplateResourceBundleTracker templateResourceBundleTracker;

    private String id;
    private String envPrefix;
    private String templatePath;
    private List<TemplateProcessor.VariableScope> variableScopePrecedence;

    @Activate
    protected void activate(DefaultTemplatedConfigSetConfig config, BundleContext context, Map<String, Object> properties) {
        id = String.valueOf(properties.get(Constants.SERVICE_PID));
        LOGGER.info("Activating config set: " + id);

        templatePath = config.templatePath();
        envPrefix = config.envPrefix();
        variableScopePrecedence = Collections.unmodifiableList(loadVariableSciptPrecedence(config.variableScopePrecedence()));

        osgiTemplatedConfigurationSetHandler = new OsgiTemplatedConfigurationSetHandler(
                id,
                configurationAdmin,
                envPrefix,
                properties,
                variableScopePrecedence);

        templateResourceBundleTracker = new TemplateResourceBundleTracker(
                context,
                templatePath,
                envPrefix,
                o -> { osgiTemplatedConfigurationSetHandler.processConfigs(o); return null; });

    }

    @Modified
    protected void update(DefaultTemplatedConfigSetConfig config, Map<String, Object> properties) {
        LOGGER.info("Updating config set: " + id);

        if (!Objects.equals(templatePath, config.templatePath())) {
            LOGGER.warn("Changing template path without restarting component is not supported");
        }
        if (!Objects.equals(envPrefix, config.envPrefix())) {
            LOGGER.warn("Changing environment prefix without restarting component is not supported");
        }
        final List<TemplateProcessor.VariableScope> newScopePrecedence = loadVariableSciptPrecedence(config.variableScopePrecedence());
        if (!Objects.equals(variableScopePrecedence, newScopePrecedence)) {
            LOGGER.warn("Changing variable scope precedence without restarting component is not supported");
        }

        osgiTemplatedConfigurationSetHandler.updateOsgiConfigs(properties);
        templateResourceBundleTracker.refreshAllBundles();
    }

    @Deactivate
    @SuppressWarnings("checkstyle:illegalcatch")
    protected void deactivate() {
        LOGGER.info("Deactivating config set: " + id);

        templateResourceBundleTracker.destroy();
        osgiTemplatedConfigurationSetHandler.destroy();

        id = null;
    }

    private List<TemplateProcessor.VariableScope> loadVariableSciptPrecedence(String value) {
        final List<TemplateProcessor.VariableScope> list = new LinkedList<>();

        if (value != null) {
            for (final String s : value.split("\\s*,\\s*")) {
                list.add(TemplateProcessor.VariableScope.valueOf(s));
            }
        }

        return list;
    }
}
