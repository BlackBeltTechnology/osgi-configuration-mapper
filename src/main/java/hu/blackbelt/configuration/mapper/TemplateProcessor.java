package hu.blackbelt.configuration.mapper;

import com.google.common.base.CaseFormat;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class TemplateProcessor {

    public static final String DOT = ".";
    public static final String UNDERSCORE = "_";

    private Map<String, Object> templateProperties;
    private final String keyPrefix;
    private final List<VariableScope> variableScopePrecedence;
    private final static String NEWLINE = System.getProperty("line.separator");

    public enum VariableScope {
        osgi, environment, system
    }

    Configuration templateConfiguration = new Configuration(Configuration.VERSION_2_3_22);

    public TemplateProcessor(Map<String, Object> props, String keyPrefix, List<VariableScope> variableScopePrecedence) {
        this.keyPrefix = keyPrefix;
        this.variableScopePrecedence = variableScopePrecedence;

        templateConfiguration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        templateConfiguration.setDefaultEncoding(Charsets.UTF_8.name());

        setTemplateProperties(props);
    }

    public void updateOsgiConfigs(Map<String, Object> props) {
        setTemplateProperties(props);
    }

    @SneakyThrows({ IOException.class, TemplateException.class })
    public boolean isProcess(String name, Optional<String> expression) {
        if (expression.isPresent()) {
            LOGGER.trace("Expression: " + expression.get());
            Template t = new Template("E-" + name,
                    new StringReader("<#if " + expression.get() + ">true<#else>false</#if>"),
                    templateConfiguration);
            StringWriter w = new StringWriter();
            t.process(templateProperties, w);
            String result = w.toString();
            LOGGER.debug("Expression result: {}", result);
            if ("true".equalsIgnoreCase(result)) {
                return true;
            }
            return false;
        } else {
            return true;
        }
    }

    @SneakyThrows({ IOException.class, TemplateException.class })
    public String resolvePid(String name, Optional<String> pidExpression) {
        if (pidExpression.isPresent()) {
            Template t = new Template("PID-" + name, pidExpression.get(), templateConfiguration);
            StringWriter w = new StringWriter();
            t.process(templateProperties, w);
            String factoryPid = w.toString().trim();
            if (!factoryPid.isEmpty()) {
                return w.toString();
            }
        }
        return null;
    }

    @SneakyThrows({ IOException.class, TemplateException.class })
    public String getConfig(ConfigurationEntry configurationEntry) {
        Template t = new Template(configurationEntry.getTemplate().toString(),
                new InputStreamReader(configurationEntry.getTemplate().openStream(), UTF_8),
                templateConfiguration);
        StringWriter w = new StringWriter();
        t.process(templateProperties, w);
        return  w.toString();
    }

    /**
     * Process configuration parameters, ie. replace special (dot) character in keys.
     *
     * @param parameters configuration parameters
     * @return processed configuration parameters
     */
    private Map<String, Object> processingParameters(final Map<String, ? extends Object> parameters) {
        return ImmutableMap.copyOf(parameters.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().replace(DOT, UNDERSCORE), Map.Entry::getValue)));
    }

    private void setTemplateProperties(Map<String, Object> props) {
        final Map<String, Object> osgiConfigs = processingParameters(props);
        final Map<String, Object> envConfigs = replacePrefixedKeys(processingParameters(System.getenv()));
        final Map systemParameters = Utils.fromDictionary(System.getProperties());
        final Map<String, Object> systemConfigs = processingParameters((Map<String, Object>) systemParameters);

        final Map<String, Object> configEntries = new HashMap<>();
        for (final VariableScope scope : variableScopePrecedence) {
            switch (scope) {
                case osgi: configEntries.putAll(osgiConfigs); break;
                case environment: configEntries.putAll(envConfigs); break;
                case system: configEntries.putAll(systemConfigs); break;
            }
        }

        Map<String, VariableScope> configTypeByKey = new HashMap<>();
        for (String k : configEntries.keySet()) {
            if (!systemConfigs.containsKey(k) && !envConfigs.containsKey(k)) {
                configTypeByKey.put(k, VariableScope.osgi);
            }  else if (!systemConfigs.containsKey(k)) {
                configTypeByKey.put(k, VariableScope.environment);
            } else {
                configTypeByKey.put(k, VariableScope.system);
            }
        }

        templateProperties = ImmutableMap.copyOf(configEntries);
        printConfigurations(configTypeByKey, templateProperties);
    }

    private Map<String, Object> replacePrefixedKeys(Map<String, Object> envConfigs) {
        // Converting <prefix>_PART1_PART2..._PARTX formatted environment variables to part1Part2..PartX format.
        Map<String, Object>  transformedEnvConfigs = Maps.newHashMap();
        for (String k : envConfigs.keySet()) {
            if (k.startsWith(keyPrefix)) {
                String newKey = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, k.substring(keyPrefix.length()));
                transformedEnvConfigs.put(newKey, envConfigs.get(k));
            } else {
                transformedEnvConfigs.put(k, envConfigs.get(k));
            }
        }
        return transformedEnvConfigs;
    }

    private void printConfigurations(Map<String, VariableScope> configTypesByKey, Map<String, Object> properties) {
        StringBuilder b = new StringBuilder();
        b.append("Properties used for configuration template: \n");
        for (String k : Ordering.natural().sortedCopy(properties.keySet())) {
            Object value = properties.get(k);
            if (k.toLowerCase().contains("password") || k.toLowerCase().contains("secret")) {
                value = "**************";
            }
            b.append(NEWLINE + "\t" +  k + " (" + configTypesByKey.get(k) + ") = " + value);
        }
        LOGGER.info(b.toString());
    }

}
