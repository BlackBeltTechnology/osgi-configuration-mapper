package hu.blackbelt.configuration.mapper;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
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
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
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
    private static final String TEST_CONFIG1_FACTORY_PID = "test1.config";
    private static final String TEST_CONFIG1_INSTANCE = "tst";
    private static final String TEST_CONFIG2_FACTORY_PID = "test2.config";
    private static final String TEST_CONFIG3_FACTORY_PID = "test3.config";
    private static final String TEST_CONFIG4_FACTORY_PID = "test4.config";
    private static final String TEST_CONFIG5_FACTORY_PID = "test5.config";
    private static final String TEST_CONFIG6_FACTORY_PID = "test6.config";

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
                        .put("context3Bool", "true")
                        .put("context4Bool", "false")
                        .put("context5aBool", "true")
                        .put("context5bBool", "false")
                        .put("context5cBool", "true")
                        .put("context6Bool", "true")
                        .put("contextVar3", VALUE3_VALUE)

                        .asOption(),

                // JUnit, Hamcrast
                mavenBundle().groupId( "org.apache.servicemix.bundles" ).artifactId( "org.apache.servicemix.bundles.hamcrest" ).version( "1.3_1"),
                junitBundles());
    }

    List<org.osgi.service.cm.Configuration> configurations;

    @Before
    public void init() throws IOException, InvalidSyntaxException {
        configurations = Arrays.asList(configAdmin.listConfigurations(null));
    }

    @Test
    public void testAllConfigSets() {
        assertThat(configurations.size(), equalTo(8));
    }


    @Test
    public void testDefaultTemplatedConfigSet() {
        final org.osgi.service.cm.Configuration testConfig = configurations.stream()
                .filter(cfg -> cfg.getPid().startsWith(TEST_CONFIG1_FACTORY_PID))
                .collect(singletonCollector());

        assertThat(testConfig, notNullValue());

        assertThat(testConfig.getProperties().get("value1"), equalTo(VALUE1_VALUE));
        assertThat(testConfig.getProperties().get("value2"), equalTo(VALUE2_VALUE));
        assertThat(testConfig.getProperties().get("value3"), equalTo(VALUE3_VALUE));
        assertThat(testConfig.getProperties().get("workDir"), equalTo(KARAF_HOME + "/test"));
        assertThat(testConfig.getProperties().get("service.factoryPid"), equalTo(TEST_CONFIG1_FACTORY_PID));
        assertThat(testConfig.getProperties().get("__osgi_templated_config_name"), equalTo(TEST_CONFIG1_FACTORY_PID + "-" + TEST_CONFIG1_INSTANCE));
    }

    @Test
    public void testConfigSetWithoutXML() {
        final org.osgi.service.cm.Configuration test2Config = configurations.stream()
                .filter(cfg -> cfg.getPid().startsWith(TEST_CONFIG2_FACTORY_PID))
                .collect(singletonCollector());

        assertThat(test2Config, notNullValue());
    }

    @Test
    public void testEnabledConfigSetWithoutFactoryPID() {
        final org.osgi.service.cm.Configuration test3Config = configurations.stream()
                .filter(cfg -> cfg.getPid().startsWith(TEST_CONFIG3_FACTORY_PID))
                .collect(singletonCollector());

        assertThat(test3Config, notNullValue());
    }

    @Test
    public void testDisabledConfigSetWithoutFactoryPID() {
        final int test4ConfigSize = configurations.stream()
                .filter(cfg -> cfg.getPid().startsWith(TEST_CONFIG4_FACTORY_PID))
                .collect(Collectors.toList()).size();

        assertThat(test4ConfigSize, equalTo(0));
    }

    @Test
    public void testConfigSetWithMultipleInstances() {
        final int test5ConfigSize = configurations.stream()
                .filter(cfg -> cfg.getPid().startsWith(TEST_CONFIG5_FACTORY_PID))
                .collect(Collectors.toList()).size();

        assertThat(test5ConfigSize, equalTo(3));

        configurations.stream()
                .filter(cfg -> cfg.getPid().startsWith(TEST_CONFIG5_FACTORY_PID))
                .forEach(cfg -> assertThat(((String)cfg.getProperties().get("__osgi_templated_config_name")), endsWith(((String)cfg.getProperties().get("name")).toLowerCase())));
    }

    @Test
    public void testTemplateWithoutFactoryPID() {
        final org.osgi.service.cm.Configuration test6Config = configurations.stream()
                .filter(cfg -> cfg.getPid().startsWith(TEST_CONFIG6_FACTORY_PID))
                .collect(singletonCollector());

        assertThat(test6Config, notNullValue());
        assertThat(test6Config.getProperties().get("name"), equalTo("TEST6"));
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