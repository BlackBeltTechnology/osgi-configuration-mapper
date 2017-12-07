package hu.blackbelt.configuration.mapper;


import com.google.common.collect.ImmutableMap;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;

import java.io.IOException;

@Component(name = "configset", immediate = true, configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = DefaultTemplatedConfigSetConfig.class)
public class DefaultTemplatedConfigSet {

    @Reference(cardinality = ReferenceCardinality.MANDATORY, policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY)
    ConfigurationAdmin configurationAdmin;

    private OsgiTemplatedConfigurationSetHandler osgiTemplatedConfigurationSetHandler;
    private TemplateResourceBundleTracker templateResourceBundleTracker;

    @Activate
    protected void activate(DefaultTemplatedConfigSetConfig config, ComponentContext componentContext)
            throws IOException {

        osgiTemplatedConfigurationSetHandler = new OsgiTemplatedConfigurationSetHandler(
                configurationAdmin,
                config.envPrefix(),
                Utils.fromDictionary(componentContext.getProperties()),
                ImmutableMap.of(),
                ImmutableMap.of());

        templateResourceBundleTracker = new TemplateResourceBundleTracker(
                componentContext.getBundleContext(),
                config.templatePath(),
                config.envPrefix(),
                o -> { osgiTemplatedConfigurationSetHandler.processConfigs(o); return null; });

    }

    @Deactivate
    @SuppressWarnings("checkstyle:illegalcatch")
    protected void deactivate() {
        templateResourceBundleTracker.destroy();
        osgiTemplatedConfigurationSetHandler.destroy();
    }

}
