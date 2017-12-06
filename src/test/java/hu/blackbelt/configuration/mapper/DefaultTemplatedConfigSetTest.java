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


    @Inject
    private ConfigurationAdmin configAdmin;

    @Configuration
    public Option[] config() {
        System.getProperties().put("KARAF_HOME", "/karaf");
        System.getProperties().put("contextVar2", "var2FromSystem");

        return options(
                cleanCaches(),
                systemPackages("org.w3c.dom.traversal"),

                mavenBundle("org.apache.felix", "org.apache.felix.scr", "2.0.12"),
                mavenBundle("org.apache.felix", "org.apache.felix.configadmin", "1.8.16"),

                // Dependencies
                mavenBundle("com.google.guava", "guava", "21.0"),
                mavenBundle("org.apache.servicemix.bundles", "org.apache.servicemix.bundles.freemarker", "2.3.22_1"),

                bundle("reference:file:target/classes"),

                newConfiguration("configset")
                        .put("templatePath", "/config-templates")
                        .put("envPrefix", "PREFIX_")
                        .put("contextBool", "true")
                        .put("contextFactoryPid", "instancePid")
                        .put("contextVar3", "var3FromOsgi")

                        .asOption(),

                // JUnit, Hamcrast
                mavenBundle().groupId( "org.apache.servicemix.bundles" ).artifactId( "org.apache.servicemix.bundles.hamcrest" ).version( "1.3_1"),
                junitBundles());
    }


    @Test
    public void testDefaultTemplatedConfigSet() throws IOException, InvalidSyntaxException {
        List<org.osgi.service.cm.Configuration> configurations = Arrays.asList(configAdmin.listConfigurations(null));
        assertThat(configurations.size(), equalTo(2));
    }


}