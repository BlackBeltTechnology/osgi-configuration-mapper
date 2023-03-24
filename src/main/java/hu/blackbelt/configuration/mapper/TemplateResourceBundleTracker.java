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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.util.tracker.BundleTracker;
import org.osgi.util.tracker.BundleTrackerCustomizer;

import java.math.BigInteger;
import java.net.URL;
import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;

@Slf4j
public class TemplateResourceBundleTracker {

    BundleTracker tracker;

    private final Map<Bundle, List<ConfigurationEntry>> configEntries = Maps.newHashMap();
    private final Map<Bundle, BigInteger> configEntriesChecksums = Maps.newHashMap();
    private final Set<Bundle> templatedBundles = Sets.newHashSet();

    private final String templatePath;
    private final Function<List<ConfigurationEntry>, Void> configurationEntriesChanged;

    public TemplateResourceBundleTracker(BundleContext bundleContext, String templatePath, String prefix,
                                         Function<List<ConfigurationEntry>, Void> configurationEntriesChanged) {
        this.templatePath = templatePath;
        this.configurationEntriesChanged = configurationEntriesChanged;

        tracker = new BundleTracker(bundleContext, Bundle.ACTIVE,
                new BundleTrackerCustomizer() {
                    public Object addingBundle(Bundle bundle, BundleEvent event) {
                        LOGGER.debug("addingBundle: " + bundle.getSymbolicName() + " Event: " + (event == null ? "NONE" : String.format("%05X", event.getType())));
                        return TemplateResourceBundleTracker.this.addingBundle(bundle);
                    }

                    public void modifiedBundle(Bundle bundle, BundleEvent event, Object object) {
                        LOGGER.debug("modifyingBundle: " + bundle.getSymbolicName());
                        TemplateResourceBundleTracker.this.modifiedBundle(bundle, false);
                    }

                    public void removedBundle(Bundle bundle, BundleEvent event, Object object) {
                        LOGGER.debug("removingBundle: " + bundle.getSymbolicName());
                        TemplateResourceBundleTracker.this.removedBundle(bundle);
                    }
                });

        tracker.open();
    }

    public void refreshAllBundles() {
        templatedBundles.forEach( bundle -> {
            LOGGER.debug("refreshingBundle: " + bundle.getSymbolicName());
            modifiedBundle(bundle, true);
        });
    }

    public void destroy() {
        tracker.close();
    }

    private Object addingBundle(Bundle bundle) {
        List<ConfigurationEntry> newEntries = collectConfigEntriesFromBundle(TemplateResourceBundleTracker.this.templatePath, bundle);
        configEntries.put(bundle, newEntries);
        LOGGER.trace("new entries: {}", newEntries);
        configEntriesChecksums.put(bundle, calculateChecksum(newEntries));
        if (newEntries.size() > 0) {
            templatedBundles.add(bundle);
            LOGGER.info("Bundle: " + bundle.getSymbolicName() + " entries adding");
            configurationEntriesChanged.apply(configEntries.values().stream().flatMap(v -> v.stream()).collect(toList()));
            return bundle;
        }
        return null;
    }

    private void modifiedBundle(Bundle bundle, boolean skipChecksum) {
        List<ConfigurationEntry> newEntries = collectConfigEntriesFromBundle(templatePath, bundle);
        BigInteger newChecksum = calculateChecksum(newEntries);
        if (skipChecksum || !configEntriesChecksums.get(bundle).equals(newChecksum)) {
            LOGGER.info("Bundle: " + bundle.getSymbolicName() + " checksum differs (or ignored), configurations have to be reloaded");
            configEntries.put(bundle, newEntries);
            LOGGER.trace("new entries: {}", newEntries);
            configEntriesChecksums.put(bundle, newChecksum);
            configurationEntriesChanged.apply(configEntries.values().stream().flatMap(v -> v.stream()).collect(toList()));
        }
    }

    private void removedBundle(Bundle bundle) {
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
        templatedBundles.remove(bundle);
    }

    private List<ConfigurationEntry> collectConfigEntriesFromBundle(String templatePath, Bundle bundle) {
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
                    Optional<URL> specUrl = Optional.empty();
                    Optional<String> instance = Optional.empty();
                    String nameWithoutExtension = s.substring(0, s.lastIndexOf("."));
                    String nameWithoutExtensionAndInstance = nameWithoutExtension.substring(0, nameWithoutExtension.lastIndexOf("-"));
                    if (allCorespondingFiles.contains(nameWithoutExtension + ".xml")) {
                        specUrl = Optional.of(bundle.getEntry(nameWithoutExtension + ".xml"));
                    } else if (allCorespondingFiles.contains(nameWithoutExtensionAndInstance + ".xml")) {
                        specUrl = Optional.of(bundle.getEntry(nameWithoutExtensionAndInstance + ".xml"));
                        instance  = Optional.of(nameWithoutExtension.replace(nameWithoutExtensionAndInstance+"-", ""));
                    }
                    return ConfigurationEntry.builder().template(templateUrl).spec(specUrl).instance(instance).build();

                }
        ).collect(toList());
    }

    private BigInteger calculateChecksum(List<ConfigurationEntry> list) {
        StringBuilder sb = new StringBuilder();
        list.stream().forEach(c -> sb.append(c.checkSum().toString()));
        return Utils.sha1(sb.toString());
    }
}
