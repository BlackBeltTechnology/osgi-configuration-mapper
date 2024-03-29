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

import com.google.common.base.Charsets;
import com.google.common.collect.Ordering;
import hu.blackbelt.osgi.configuration.mapper.v1.xml.ns.definition.Components;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.*;

import static hu.blackbelt.configuration.mapper.ConfigState.CHECKSUMCHANGE;
import static hu.blackbelt.configuration.mapper.ConfigState.FOREIGN;
import static hu.blackbelt.configuration.mapper.ConfigState.NEW;
import static hu.blackbelt.configuration.mapper.ConfigState.UNCHANGED;
import static hu.blackbelt.configuration.mapper.Utils.fromDictionary;
import static hu.blackbelt.configuration.mapper.Utils.getPidName;
import static hu.blackbelt.configuration.mapper.Utils.loadProperties;
import static hu.blackbelt.configuration.mapper.Utils.parsePid;
import static hu.blackbelt.configuration.mapper.Utils.sha1;

/**
 * Tracking and mapping all configurations.
 * It tracks bundles and any bundle have .template file inside the given configPath processing the templates.
 * The template context is the default given properties and defaultValues.
 * The template name before the .template is he PID of the templated process. When .xml file given, the templated
 * factory PID(s) and a condition(s) (create component instance or not) can be specified. Multiple instances are also
 * supported.
 */
@Slf4j
public class OsgiTemplatedConfigurationSetHandler {
    private static final String CONFIGURATION_CHECKSUM_PROPERTY_NAME = "__osgi_templated_checksum";
    private static final String CONFIGURATION_PROPERTY_NAME = "__osgi_templated_config_name";
    private static final String CONFIGURATION_CREATED_BY_PROPERTY_NAME = "__osgi_templated_created_by";

    public static final String UPDATING_CONFIGUTRATION = "Updating configuration pid: %s configEntry: %s state: %s entries: %s";
    private final static String NEWLINE = System.getProperty("line.separator");

    private final String id;
    private final ConfigurationAdmin configAdmin;
    private final String envPrefix;
    private final TemplateProcessor templateProcessor;
    private final Unmarshaller unmarshaller;

    @SneakyThrows(JAXBException.class)
    public OsgiTemplatedConfigurationSetHandler(String id, ConfigurationAdmin configAdmin, String envPrefix,
                                                Map<String, Object> properties,
                                                List<TemplateProcessor.VariableScope> variableScopePrecedence) {
        this.id = id;
        this.configAdmin = configAdmin;
        this.envPrefix = envPrefix;
        templateProcessor = new TemplateProcessor(properties, envPrefix, variableScopePrecedence);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
        final JAXBContext jc = JAXBContext.newInstance("hu.blackbelt.osgi.configuration.mapper.v1.xml.ns.definition", getClass().getClassLoader());
        Thread.currentThread().setContextClassLoader(cl);
        unmarshaller = jc.createUnmarshaller();
    }

    public void updateOsgiConfigs(Map<String, Object> properties) {
        templateProcessor.updateOsgiConfigs(properties);
    }

    @SneakyThrows(JAXBException.class)
    public void processConfigs(List<ConfigurationEntry> entries) {
        // Updating or creating corresponding configurations.
        final Set<Configuration> processedConfigs = new HashSet<>();
        for (ConfigurationEntry entry : entries) {
            LOGGER.debug("Processing {}", entry.template);
            if (entry.getSpec().isPresent()) {
                final Components components = (Components)unmarshaller.unmarshal(entry.getSpec().get());
                if (components == null || components.getComponents().isEmpty()) {
                    LOGGER.warn("Missing component instances in configuration mapper XML");
                } else {
                    if (!entry.getInstance().isPresent()) {
                        // instances without factory PID and with expression PID will be created based on template without instance name
                        components.getComponents().stream().filter(c -> c.getFactoryPid() == null || c.getFactoryPid().contains("$")).forEach(c -> {
                            final String pidName = c.getFactoryPid() != null ? entry.getPidBaseName() + "-" + templateProcessor.resolvePid(entry.getPidBaseName(), Optional.ofNullable(c.getFactoryPid())) : entry.getPidBaseName();
                            createInstance(entry, pidName, Optional.ofNullable(c.getCondition()), processedConfigs);
                        });
                    } else {
                        // matching factory PID will be instantiated
                        components.getComponents().stream().filter(c -> Objects.equals(
                                templateProcessor.resolvePid(entry.getPidBaseName(), Optional.ofNullable(c.getFactoryPid())), entry.getInstance().get())).forEach(c ->
                            createInstance(entry, entry.getPidBaseName() + "-" + entry.getInstance().get(), Optional.ofNullable(c.getCondition()), processedConfigs));
                    }
                }
            } else {
                // XML file is not exists
                createInstance(entry, entry.getPidBaseName(), Optional.empty(), processedConfigs);
            }
        }

        getConfigurations().forEach(c -> {
            if (!processedConfigs.contains(c)) {
                String pid = c.getPid();
                LOGGER.info("Removing config: {}-{}", pid, c.getFactoryPid());
                try {
                    c.delete();
                } catch (IOException | RuntimeException ex) {
                    LOGGER.error("Unable to delete configuration of {}", pid, ex);
                }

            }
        });
    }

    public void destroy() {
        for (Configuration configuration : getConfigurations()) {
            try {
                configuration.delete();
            } catch (IOException | RuntimeException e) {
                LOGGER.error("Unable to delete configuration", e);
            }
        }
    }

    private void createInstance(final ConfigurationEntry entry, final String pidName, final Optional<String> condition, final Set<Configuration> processedConfigs ) {
        if (templateProcessor.isProcess(pidName, condition)) {
            try {
                Configuration config = setConfig(entry, pidName,
                        new ByteArrayInputStream(templateProcessor.getConfig(entry).getBytes(Charsets.UTF_8)));
                final String pid = config.getPid();
                processedConfigs.add(config);
                LOGGER.debug("Created/updated config with PID: {}", pid);
            } catch (Exception ex) {
                LOGGER.error("Unable to create config", ex);
            }
        }
    }

    private List<Configuration> getConfigurations() {
        try {
            final Configuration[] configurations = configAdmin.listConfigurations("(" + CONFIGURATION_CREATED_BY_PROPERTY_NAME + "=" + id + ")");
            return configurations != null ? Arrays.asList(configurations) : Collections.emptyList();
        } catch (InvalidSyntaxException | IOException ex) {
            LOGGER.error("Unable to get configurations", ex);
            return Collections.emptyList();
        }
    }

    /**
     * Set the configuration based on the config file.
     *
     * @param name pid name
     * @param value content
     * @return sha1 of configuration
     * @throws Exception
     */
    @SuppressWarnings({"checkstyle:executablestatementcount", "checkstyle:methodlength"})
    @SneakyThrows(IOException.class)
    private Configuration setConfig(ConfigurationEntry configurationEntry, String name, InputStream value) {
        Dictionary<String, Object> ht = loadProperties(value);

        String[] pid = parsePid(name);
        Configuration config = getConfiguration(pid[0], pid[1]);

        BigInteger checksum = sha1(fromDictionary(ht));

        ConfigState state = ConfigState.UNCHANGED;

        if (config.getProperties() == null) {
            state = NEW;
        } else if (config.getProperties().get(CONFIGURATION_PROPERTY_NAME) == null
                || !config.getProperties().get(CONFIGURATION_PROPERTY_NAME).equals(getPidName(pid[0], pid[1]))) {
            state = FOREIGN;
        } else if (config.getProperties().get(CONFIGURATION_CHECKSUM_PROPERTY_NAME) == null) {
            state = FOREIGN;
        } else if (!(new BigInteger((String) config.getProperties().get(CONFIGURATION_CHECKSUM_PROPERTY_NAME))).equals(checksum)) {
            state = CHECKSUMCHANGE;
        } else if (new BigInteger((String) config.getProperties().get(CONFIGURATION_CHECKSUM_PROPERTY_NAME)).equals(checksum)) {
            LOGGER.debug("Configuration is not updated because of unchanged checksum");
        }

        // Invalid states. We remove the config and receate la
        if (state == FOREIGN) {
            config.delete();
            config = getConfiguration(pid[0], pid[1]);
        }

        if (state != UNCHANGED) {
            LOGGER.info(String.format(UPDATING_CONFIGUTRATION, name, configurationEntry.toString(), state,  formatConfig(fromDictionary(ht))));

            ht.put(CONFIGURATION_CHECKSUM_PROPERTY_NAME, checksum.toString());
            ht.put(CONFIGURATION_PROPERTY_NAME, getPidName(pid[0], pid[1]));
            ht.put(CONFIGURATION_CREATED_BY_PROPERTY_NAME, id);
            if (config.getBundleLocation() != null) {
                config.setBundleLocation(null);
            }
            config.update(ht);
            LOGGER.info("Created/updated config with PID: {}", getPidName(pid[0], pid[1]));
        }
        return config;
    }

    @SneakyThrows(IOException.class)
    private Configuration getConfiguration(String pid, String factoryPid)  {

        Configuration oldConfiguration = findExistingConfiguration(pid, factoryPid);
        if (oldConfiguration != null) {
            // services.info("Updating configuration from " + getPidBaseName(pid, factoryPid));
            return oldConfiguration;
        } else {
            Configuration newConfiguration;
            if (factoryPid != null) {
                newConfiguration = configAdmin.createFactoryConfiguration(pid, null);
            } else {
                newConfiguration = configAdmin.getConfiguration(pid, null);
            }
            return newConfiguration;
        }
    }

    @SneakyThrows({ IOException.class, InvalidSyntaxException.class })
    private Configuration findExistingConfiguration(String pid, String factoryPid) {
        String filter = "(" + CONFIGURATION_PROPERTY_NAME + "=" + getPidName(pid, factoryPid) + ")";
        Configuration[] configurations = configAdmin.listConfigurations(filter);
        if (configurations != null && configurations.length > 0) {
            return configurations[0];
        } else {
            return null;
        }
    }

    private String formatConfig(Map<String, Object> properties) {
        StringBuilder b = new StringBuilder();
        for (String k : Ordering.natural().sortedCopy(properties.keySet())) {
            Object value = properties.get(k);
            if (k.toLowerCase().contains("password") || k.toLowerCase().contains("secret")) {
                value = "**************";
            }
            b.append(NEWLINE + "\t" +  k + " = " + value);
        }
        return b.toString();
    }

}
