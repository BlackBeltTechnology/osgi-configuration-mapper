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
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

import static hu.blackbelt.configuration.mapper.Utils.readUrl;
import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class TemplateProcessor {

    public static final String DOT = ".";
    public static final String UDERSCORE = "_";

    private final Map<String, Object> templateProperties;
    private final String keyPrefix;
    private final Map<String, Class> defaultTypes;
    private final Map<String, Object> defaultValues;
    private final static String NEWLINE = System.getProperty("line.separator");

    Configuration templateConfiguration = new Configuration(Configuration.VERSION_2_3_22);

    public TemplateProcessor(Map props, String keyPrefix, Map<String, Class> defaultTypes, Map<String, Object> defaultValues) {
        this.keyPrefix = keyPrefix;
        this.defaultTypes = defaultTypes;
        this.defaultValues = defaultValues;

        templateConfiguration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        templateConfiguration.setDefaultEncoding(Charsets.UTF_8.name());


        Map<String, Object> defaultConfigs = processingParameters(defaultValues);
        Map<String, Object> envConfigs = replacePrefixedKeys(processingParameters(System.getenv()));
        Map<String, Object> osgiConfigs = processingParameters(props);
        Map<String, Object> systemConfigs = processingParameters(System.getProperties());


        Map<String, Object> configEntries = new HashMap<>();
        configEntries.putAll(defaultConfigs);
        configEntries.putAll(osgiConfigs);
        configEntries.putAll(envConfigs);
        configEntries.putAll(systemConfigs);

        Map<String, String> configTypeByKey = new HashMap<>();
        for (String k : configEntries.keySet()) {
            if (!osgiConfigs.containsKey(k) && !systemConfigs.containsKey(k) && !envConfigs.containsKey(k)) {
                configTypeByKey.put(k, "default");        
            } else if (!systemConfigs.containsKey(k) && !envConfigs.containsKey(k)) {
                configTypeByKey.put(k, "osgi");
            }  else if (!systemConfigs.containsKey(k)) {
                configTypeByKey.put(k, "env");
            } else {
                configTypeByKey.put(k, "system");
            }
        }

        templateProperties = ImmutableMap.copyOf(configEntries);
        printConfigurations(configTypeByKey, templateProperties);
    }

    @SneakyThrows({ IOException.class, TemplateException.class })
    public boolean isProcess(ConfigurationEntry configurationEntry) {
        if (configurationEntry.getExpression().isPresent()) {
            Template t = new Template(configurationEntry.getExpression().get().toString(),
                    new StringReader("<#if " + readUrl(configurationEntry.getExpression().get()) + ">true<#else>false</#if>"),
                    templateConfiguration);
            StringWriter w = new StringWriter();
            t.process(templateProperties, w);
            if ("true".equalsIgnoreCase(w.toString())) {
                return true;
            }
            return false;
        } else {
            return true;
        }
    }

    @SneakyThrows({ IOException.class, TemplateException.class })
    public String getConfigPidName(ConfigurationEntry configurationEntry) {
        String pidName = configurationEntry.getPidBaseName();
        if (configurationEntry.getPid().isPresent()) {
            Template t = new Template(configurationEntry.getPid().get().toString(),
                    new StringReader(readUrl(configurationEntry.getPid().get())), templateConfiguration);
            StringWriter w = new StringWriter();
            t.process(templateProperties, w);
            String factoryPid = w.toString().trim();
            if (!factoryPid.isEmpty()) {
                return pidName + "-" + w.toString();
            }
        }
        return pidName;
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

    @SuppressWarnings({"checkstyle:executablestatementcount", "checkstyle:methodlength"})
    public Map<String, Object> processingParameters(Object dictionary) {
        Map<String, Object> ret = new HashMap<>();

        Map map;
        if (dictionary instanceof Dictionary) {
            map = Utils.fromDictionary((Dictionary) dictionary);
        } else if (dictionary instanceof Map) {
            map = (Map) dictionary;
        } else {
            throw new IllegalArgumentException("processingParameter type can be Map or Dictionary");
        }

        for (Object key : map.keySet()) {
            String k = (String) key;
            String newk = k;
            if (k.contains(DOT)) {
                newk = k.replace(DOT, UDERSCORE).toUpperCase();
            }
            Class propType = String.class;
            if (defaultTypes.containsKey(k)) {
                propType = defaultTypes.get(k);
            }
            Object propDefault = null;
            if (defaultValues.containsKey(k)) {
                propDefault = defaultValues.get(k);
            }

            if (propType == String.class) {
                ret.put(newk, Utils.toString(map.get(k), (String) propDefault));
            } else if (propType == Long.class) {
                ret.put(newk, Utils.toLong(map.get(k), (Long) propDefault));
            } else if (propType == Boolean.class) {
                ret.put(newk, Utils.toBoolean(map.get(k), (Boolean) propDefault));
            } else if (propType == Integer.class) {
                ret.put(newk, Utils.toInteger(map.get(k), (Integer) propDefault));
            } else if (propType == Double.class) {
                ret.put(newk, Utils.toDouble(map.get(k), (Double) propDefault));
            } else if (propType == String[].class) {
                ret.put(newk, Utils.toStringArray(map.get(k), (String[]) propDefault));
            } else if (propType == Map.class) {
                ret.put(newk, Utils.toMap(map.get(k), (String[]) propDefault));
            } else {
                throw new IllegalArgumentException("Unknown type: " + propType);
            }
        }
        return ImmutableMap.copyOf(ret);
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

    private void printConfigurations(Map<String, String> configTypesByKey, Map<String, Object> properties) {
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
