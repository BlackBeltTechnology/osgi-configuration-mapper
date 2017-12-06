package hu.blackbelt.configuration.mapper;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.util.tracker.BundleTracker;
import org.osgi.util.tracker.BundleTrackerCustomizer;

import java.math.BigInteger;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;

@Slf4j
public class TemplateResourceBundleTracker {

    BundleTracker tracker;

    Map<Bundle, List<ConfigurationEntry>> configEntries = Maps.newHashMap();
    Map<Bundle, BigInteger> configEntriesChecksums = Maps.newHashMap();

    public TemplateResourceBundleTracker(BundleContext bundleContext, String templatePath, String prefix,
                                         Function<List<ConfigurationEntry>, Void> configurationEntriesChanged) {

        tracker = new BundleTracker(bundleContext, Bundle.ACTIVE,
                new BundleTrackerCustomizer() {
                    public Object addingBundle(Bundle bundle, BundleEvent event) {
                        LOGGER.debug("addingBundle: " + bundle.getSymbolicName() + " Event: " + (event == null ? "NONE" : String.format("%05X", event.getType())));
                        List<ConfigurationEntry> newEntries = collectConfigEntriesFormBundle(templatePath, bundle);
                        configEntries.put(bundle, newEntries);
                        configEntriesChecksums.put(bundle, calculateChecksum(newEntries));
                        if (newEntries.size() > 0) {
                            LOGGER.info("Bundle: " + bundle.getSymbolicName() + " entries adding");
                            configurationEntriesChanged.apply(configEntries.values().stream().flatMap(v -> v.stream()).collect(toList()));
                            return bundle;
                        }
                        return null;
                    }

                    public void modifiedBundle(Bundle bundle, BundleEvent event, Object object) {
                        LOGGER.debug("modifyingBundle: " + bundle.getSymbolicName());
                        List<ConfigurationEntry> newEntries = collectConfigEntriesFormBundle(templatePath, bundle);
                        BigInteger newChecksum = calculateChecksum(newEntries);
                        if (!configEntriesChecksums.get(bundle).equals(newChecksum)) {
                            LOGGER.info("Bundle: " + bundle.getSymbolicName() + " checksum differs, configurations have to be reloaded");
                            configEntries.put(bundle, newEntries);
                            configEntriesChecksums.put(bundle, newChecksum);
                            configurationEntriesChanged.apply(configEntries.values().stream().flatMap(v -> v.stream()).collect(toList()));
                        }

                    }

                    public void removedBundle(Bundle bundle, BundleEvent event, Object object) {
                        LOGGER.debug("removingBundle: " + bundle.getSymbolicName());
                        boolean haveToRefresh = false;
                        if (configEntries.get(bundle).size() > 0) {
                            haveToRefresh = true;
                        }
                        configEntries.remove(bundle);
                        configEntriesChecksums.remove(bundle);
                        if (haveToRefresh) {
                            LOGGER.info("Bundle: " + bundle.getSymbolicName() + " entries removing");
                            configurationEntriesChanged.apply(configEntries.values().stream().flatMap(v -> v.stream()).collect(toList()));
                        }
                    }
                });

        tracker.open();
    }

    public void destroy() {
        tracker.close();
    }


    private List<ConfigurationEntry> collectConfigEntriesFormBundle(String templatePath, Bundle bundle) {
        String templatedPathCorrected = templatePath;
        if (!templatePath.endsWith("/")) {
            templatedPathCorrected = templatedPathCorrected + "/";
        }
        if (!templatePath.startsWith("/")) {
            templatedPathCorrected =  "/" + templatedPathCorrected;
        }

        Enumeration<String> paths = bundle.getEntryPaths(templatedPathCorrected);
        if (paths == null) {
            return ImmutableList.of();
        }

        List<String> allCorespondingFiles = Collections.list(paths).stream()
                .collect(toList());

        return allCorespondingFiles.stream().filter(s -> s.endsWith(".template")).map(s ->
                {
                    URL templateUrl = bundle.getEntry(s);
                    Optional<URL> pidUrl = Optional.empty();
                    Optional<URL> expressionUrl = Optional.empty();
                    String nameWithoutExtension = s.substring(0, s.lastIndexOf("."));
                    if (allCorespondingFiles.contains(nameWithoutExtension + ".pid")) {
                        pidUrl = Optional.of(bundle.getEntry(nameWithoutExtension + ".pid"));
                    }
                    if (allCorespondingFiles.contains(nameWithoutExtension + ".expression")) {
                        expressionUrl = Optional.of(bundle.getEntry(nameWithoutExtension + ".expression"));
                    }
                    return ConfigurationEntry.builder().template(templateUrl).expression(expressionUrl).pid(pidUrl).build();

                }
        ).collect(toList());
    }

    private BigInteger calculateChecksum(List<ConfigurationEntry> list) {
        StringBuilder sb = new StringBuilder();
        list.stream().forEach(c -> sb.append(c.checkSum().toString()));
        return Utils.sha1(sb.toString());
    }
}
