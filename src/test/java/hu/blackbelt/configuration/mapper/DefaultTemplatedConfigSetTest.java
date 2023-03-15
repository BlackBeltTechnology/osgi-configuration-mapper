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
import static org.hamcrest.Matchers.*;
import static org.ops4j.pax.exam.CoreOptions.*;
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
    private static final String TEST_CONFIG7_FACTORY_PID = "test7.config";
    private static final String TEST_CONFIG7_INSTANCE_PID = "T7";
    private static final String TEST_CONFIG8_FACTORY_PID = "test8.config";
    public static final String SYSTEM_VARIABLE = "systemVariable";

    @Inject
    private ConfigurationAdmin configAdmin;

    @Configuration
    public Option[] config() {
        System.getProperties().put("KARAF_HOME", KARAF_HOME);
        System.getProperties().put(SYSTEM_VARIABLE, SYSTEM_VARIABLE);
        System.getProperties().put("contextVar2", VALUE2_VALUE);

        return options(
                cleanCaches(),

                bootDelegationPackage("com.sun.*"),

                systemPackages("org.w3c.dom.traversal"),

                environment("PREFIX_ENV=testFromEnv"),

                mavenBundle("org.apache.servicemix.specs", "org.apache.servicemix.specs.activation-api-1.2.1", "1.2.1_3"),

//                mavenBundle("javax.xml.bind", "jaxb-api", "2.3.1"),
//                mavenBundle("com.sun.xml.bind", "jaxb-osgi", "2.3.3"),
//                mavenBundle("org.apache.servicemix.bundles", "org.apache.servicemix.bundles.jaxb-runtime", "2.3.2_2"),

                mavenBundle("org.apache.servicemix.specs", "org.apache.servicemix.specs.stax-api-1.0", "2.9.0"),
                mavenBundle("org.apache.servicemix.specs", "org.apache.servicemix.specs.jaxb-api-2.2", "2.2.0"),
                mavenBundle("org.apache.servicemix.bundles", "org.apache.servicemix.bundles.jaxb-impl", "2.2.11_1"),


                mavenBundle("org.osgi", "org.osgi.util.promise", "1.1.1"),
                mavenBundle("org.osgi", "org.osgi.util.function", "1.1.0"),
                mavenBundle("org.apache.felix", "org.apache.felix.scr", "2.1.20"),
                mavenBundle("org.apache.felix", "org.apache.felix.configadmin", "1.9.16"),


                mavenBundle("org.slf4j", "slf4j-api", "1.6.1"),
                mavenBundle("ch.qos.logback", "logback-core", "1.0.6"),
                mavenBundle("ch.qos.logback", "logback-classic", "1.0.6"),

                // Dependencies
                mavenBundle("com.google.guava", "failureaccess", "1.0.1"),
                mavenBundle("com.google.guava", "guava", "27.1-jre"),
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
                        .put("template7FactoryPid", TEST_CONFIG7_INSTANCE_PID)
                        .put("context7Bool", "true")

                        .asOption(),

                // JUnit, Hamcrast
                mavenBundle().groupId( "org.apache.servicemix.bundles" ).artifactId( "org.apache.servicemix.bundles.hamcrest" ).version( "1.3_1"),
                junitBundles());
    }

    List<org.osgi.service.cm.Configuration> configurations;

    @Before
    public void init() throws IOException, InvalidSyntaxException {
        configurations = Arrays.asList(configAdmin.listConfigurations(null));

        configurations.stream().forEach(cfg -> LOGGER.info(" - {} CFG: {}", cfg.getPid(), cfg.getProperties()));
    }

    @Test
    public void testAllConfigSets() {
        assertThat(configurations.size(), equalTo(12));
    }

    @Test
    public void testDefaultTemplatedConfigSet() {
        final org.osgi.service.cm.Configuration testConfig = configurations.stream()
                .filter(cfg -> cfg.getPid().startsWith(TEST_CONFIG1_FACTORY_PID))
                .collect(singletonCollector());

        assertThat(testConfig, notNullValue());

        assertThat(testConfig.getProperties().get("env"), equalTo("testFromEnv"));
        assertThat(testConfig.getProperties().get("anotherEnv"), equalTo("testFromAnotherEnv"));
        assertThat(testConfig.getProperties().get(SYSTEM_VARIABLE), equalTo(SYSTEM_VARIABLE));

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
    public void testConfigSetWithoutXMLWithInstanceName() {
        final org.osgi.service.cm.Configuration test8Config = configurations.stream()
                .filter(cfg -> cfg.getPid().startsWith(TEST_CONFIG8_FACTORY_PID))
                .collect(singletonCollector());

        assertThat(test8Config, notNullValue());
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

    @Test
    public void testTemplateWithExpressionPID() {
        final org.osgi.service.cm.Configuration test7aConfig = configurations.stream()
                .filter(cfg -> cfg.getPid().startsWith(TEST_CONFIG7_FACTORY_PID) && ((String)cfg.getProperties().get("__osgi_templated_config_name")).endsWith("-a"))
                .collect(singletonCollector());

        assertThat(test7aConfig, notNullValue());
        assertThat(test7aConfig.getProperties().get("name"), equalTo("TEST7-A"));

        final org.osgi.service.cm.Configuration test7DefaultConfig = configurations.stream()
                .filter(cfg -> cfg.getPid().startsWith(TEST_CONFIG7_FACTORY_PID) && ((String)cfg.getProperties().get("__osgi_templated_config_name")).equals(TEST_CONFIG7_FACTORY_PID))
                .collect(singletonCollector());

        assertThat(test7DefaultConfig, notNullValue());
        assertThat(test7DefaultConfig.getProperties().get("name"), equalTo("TEST7"));
        assertThat(test7DefaultConfig.getProperties().get("instance"), equalTo(TEST_CONFIG7_INSTANCE_PID));

        final org.osgi.service.cm.Configuration test7ExpressionConfig = configurations.stream()
                .filter(cfg -> cfg.getPid().startsWith(TEST_CONFIG7_FACTORY_PID) && ((String)cfg.getProperties().get("__osgi_templated_config_name")).endsWith("-" + TEST_CONFIG7_INSTANCE_PID))
                .collect(singletonCollector());

        assertThat(test7ExpressionConfig, notNullValue());
        assertThat(test7ExpressionConfig.getProperties().get("name"), equalTo("TEST7"));
        assertThat(test7ExpressionConfig.getProperties().get("instance"), equalTo(TEST_CONFIG7_INSTANCE_PID));
        assertThat((String)test7ExpressionConfig.getProperties().get("service.pid"), startsWith(TEST_CONFIG7_FACTORY_PID + "."));
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
