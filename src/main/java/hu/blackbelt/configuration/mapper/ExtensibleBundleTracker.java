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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.util.tracker.BundleTracker;
import org.osgi.util.tracker.BundleTrackerCustomizer;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/**
 *
 * Extensible bundle tracker. Takes several BundleTrackerCustomizers and
 * propagates bundle events to all of them.
 *
 * Primary customizer may return tracking object,
 * which will be passed to it during invocation of
 * {@link BundleTrackerCustomizer#removedBundle(Bundle, BundleEvent, Object)}}
 *
 *
 * This extender modifies behaviour to not leak platform thread
 * in {@link BundleTrackerCustomizer#addingBundle(Bundle, BundleEvent)}
 * but deliver this event from its own single threaded executor.
 *
 * If bundle is removed before event for adding bundle was executed,
 * that event is cancelled. If addingBundle event is currently in progress
 * or was already executed, platform thread is block untill addingBundle
 * finishes so bundle could be removed correctly in platform thread.
 *
 *
 * Method {@link BundleTrackerCustomizer#removedBundle(Bundle, BundleEvent, Object)}
 * is never invoked on registered trackers.
 *
 * @param <T>
 */
@Slf4j
public final class ExtensibleBundleTracker<T> extends BundleTracker<Future<T>> {
    private static final ThreadFactory THREAD_FACTORY = new ThreadFactoryBuilder()
            .setNameFormat("config-bundle-tracker-%d").build();
    private final ExecutorService eventExecutor;
    private final BundleTrackerCustomizer<T> primaryTracker;
    private final BundleTrackerCustomizer<?>[] additionalTrackers;

    public ExtensibleBundleTracker(final BundleContext context, final BundleTrackerCustomizer<T> primaryBundleTrackerCustomizer,
                                   final BundleTrackerCustomizer<?>... additionalBundleTrackerCustomizers) {
        this(context, Bundle.ACTIVE, primaryBundleTrackerCustomizer, additionalBundleTrackerCustomizers);
    }

    public ExtensibleBundleTracker(final BundleContext context, final int bundleState,
                                   final BundleTrackerCustomizer<T> primaryBundleTrackerCustomizer,
                                   final BundleTrackerCustomizer<?>... additionalBundleTrackerCustomizers) {
        super(context, bundleState, null);
        this.primaryTracker = primaryBundleTrackerCustomizer;
        this.additionalTrackers = additionalBundleTrackerCustomizers;
        eventExecutor = Executors.newSingleThreadExecutor(THREAD_FACTORY);
        LOGGER.trace("Registered as extender with context {} and bundle state {}", context, bundleState);
    }

    @Override
    public Future<T> addingBundle(final Bundle bundle, final BundleEvent event) {
        LOGGER.trace("Submiting AddingBundle for bundle {} and event {} to be processed asynchronously", bundle,event);
        Future<T> future = eventExecutor.submit(new Callable<T>() {
            @Override
            public T call() throws Exception {
                try {
                    T primaryTrackerRetVal = primaryTracker.addingBundle(bundle, event);

                    forEachAdditionalBundle(new BundleStrategy() {
                        @Override
                        public void execute(final BundleTrackerCustomizer<?> tracker) {
                            tracker.addingBundle(bundle, event);
                        }
                    });
                    LOGGER.trace("AddingBundle for {} and event {} finished successfully",bundle,event);
                    return primaryTrackerRetVal;
                } catch (Exception e) {
                    LOGGER.error("Failed to add bundle {}", bundle, e);
                    throw e;
                }
            }
        });
        return future;
    }

    @Override
    public void modifiedBundle(final Bundle bundle, final BundleEvent event, final Future<T> object) {
        // Intentionally NOOP

    }

    @Override
    public void removedBundle(final Bundle bundle, final BundleEvent event, final Future<T> object) {
        if(!object.isDone() && object.cancel(false)) {
            // We canceled adding event before it was processed
            // so it is safe to return
            LOGGER.trace("Adding Bundle event for {} was cancelled. No additional work required.",bundle);
            return;
        }
        try {
            LOGGER.trace("Invoking removedBundle event for {}",bundle);
            primaryTracker.removedBundle(bundle, event, object.get());
            forEachAdditionalBundle(new BundleStrategy() {
                @Override
                public void execute(final BundleTrackerCustomizer<?> tracker) {
                    tracker.removedBundle(bundle, event, null);
                }
            });
            LOGGER.trace("Removed bundle event for {} finished successfully.",bundle);
        } catch (Exception e) {
            LOGGER.error("Failed to remove bundle {}", bundle, e);
        }
    }

    private void forEachAdditionalBundle(final BundleStrategy lambda) {
        for (BundleTrackerCustomizer<?> trac : additionalTrackers) {
            lambda.execute(trac);
        }
    }

    private static interface BundleStrategy {
        void execute(BundleTrackerCustomizer<?> tracker);
    }

}
