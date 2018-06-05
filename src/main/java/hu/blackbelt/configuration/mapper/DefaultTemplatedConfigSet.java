package hu.blackbelt.configuration.mapper;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.Designate;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

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

    @Activate
    protected void activate(DefaultTemplatedConfigSetConfig config, BundleContext context, Map<String, Object> properties)
            throws IOException {
        id = String.valueOf(properties.get(Constants.SERVICE_PID));
        LOGGER.info("Activating config set: " + id);

        templatePath = config.templatePath();
        envPrefix = config.envPrefix();

        osgiTemplatedConfigurationSetHandler = new OsgiTemplatedConfigurationSetHandler(
                id,
                configurationAdmin,
                envPrefix,
                properties,
                ImmutableMap.of(),
                ImmutableMap.of());

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

}
