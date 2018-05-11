package hu.blackbelt.configuration.mapper;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.ConfigurationAdmin;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.ops4j.pax.exam.CoreOptions.bundle;
import static org.ops4j.pax.exam.CoreOptions.cleanCaches;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.systemPackages;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

@RunWith(PaxExam.class)
//@ExamReactorStrategy(PerMethod.class)
@Slf4j
public class DefaultTemplatedConfigSetTest {

    private static final String CONFIGSET_PID = "configset";
    private static final String VALUE1_VALUE = "contextVar1TemplateDefault";
    private static final String VALUE2_VALUE = "var2FromSystem";
    private static final String VALUE3_VALUE = "var3FromOsgi";
    private static final String KARAF_HOME = "/karaf";
    private static final String TEST_CONFIG_FACTORY_PID = "test.config";
    private static final String TEST_CONFIG_INSTANCE = "instancePid";


    @Inject
    private ConfigurationAdmin configAdmin;

    @Configuration
    public Option[] config() {
        System.getProperties().put("KARAF_HOME", KARAF_HOME);
        System.getProperties().put("contextVar2", VALUE2_VALUE);

        return options(
                cleanCaches(),
                systemPackages("org.w3c.dom.traversal"),

                mavenBundle("org.apache.felix", "org.apache.felix.scr", "2.0.12"),
                mavenBundle("org.apache.felix", "org.apache.felix.configadmin", "1.8.16"),

                // Dependencies
                mavenBundle("com.google.guava", "guava", "21.0"),
                mavenBundle("org.apache.servicemix.bundles", "org.apache.servicemix.bundles.freemarker", "2.3.22_1"),

                bundle("reference:file:target/classes"),

                newConfiguration(CONFIGSET_PID)
                        .put("templatePath", "/config-templates")
                        .put("envPrefix", "PREFIX_")
                        .put("contextBool", "true")
                        .put("contextFactoryPid", TEST_CONFIG_INSTANCE)
                        .put("contextVar3", VALUE3_VALUE)

                        .asOption(),

                // JUnit, Hamcrast
                mavenBundle().groupId( "org.apache.servicemix.bundles" ).artifactId( "org.apache.servicemix.bundles.hamcrest" ).version( "1.3_1"),
                junitBundles());
    }


    @Test
    public void testDefaultTemplatedConfigSet() throws IOException, InvalidSyntaxException {
        List<org.osgi.service.cm.Configuration> configurations = Arrays.asList(configAdmin.listConfigurations(null));
        assertThat(configurations.size(), equalTo(2));

        final org.osgi.service.cm.Configuration configuration = configurations.stream()
                .filter(cfg -> !CONFIGSET_PID.equals(cfg.getPid()))
                .collect(singletonCollector());

        assertThat(configuration.getProperties().get("value1"), equalTo(VALUE1_VALUE));
        assertThat(configuration.getProperties().get("value2"), equalTo(VALUE2_VALUE));
        assertThat(configuration.getProperties().get("value3"), equalTo(VALUE3_VALUE));
        assertThat(configuration.getProperties().get("workDir"), equalTo(KARAF_HOME + "/test"));
        assertThat(configuration.getProperties().get("service.factoryPid"), equalTo(TEST_CONFIG_FACTORY_PID));
        assertThat(configuration.getProperties().get("__osgi_templated_config_name"), equalTo(TEST_CONFIG_FACTORY_PID + "-" + TEST_CONFIG_INSTANCE));
    }


    public static <T> Collector<T, ?, T> singletonCollector() {
        return Collectors.collectingAndThen(
                Collectors.toList(),
                list -> {
                    if (list.size() != 1) {
                        throw new IllegalStateException();
                    }
                    return list.get(0);
                }
        );
    }
}