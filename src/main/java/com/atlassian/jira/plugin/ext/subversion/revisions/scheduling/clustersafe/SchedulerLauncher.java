package com.atlassian.jira.plugin.ext.subversion.revisions.scheduling.clustersafe;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.propertyset.JiraPropertySetFactory;
import com.atlassian.jira.service.ServiceManager;
import com.atlassian.plugin.event.events.PluginEnabledEvent;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import javax.annotation.concurrent.GuardedBy;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Coordinate all the startup information to decide when it is safe to do complicated work.
 * <p/>
 * This class copies the approach used by JIRA Agile to solve this problem.  In particular,
 * it watches as the various events occur.  The actual launching, creation of initial data,
 * and scheduling of background tasks is delayed until all of the pieces of the puzzle are
 * in place.  The other components are initialized explicitly by this launcher, though there
 * are other strategies (like using an event to decouple this interaction) that might be
 * better.
 * </p>
 *
 * @since v2.0
 */
public class SchedulerLauncher implements LifecycleAware, InitializingBean, DisposableBean {
    private static final Logger logger = LoggerFactory.getLogger(SchedulerLauncher.class);

    static final String PLUGIN_KEY = "com.atlassian.jira.plugin.ext.subversion";
    private static final long DEFAULT_SERVICE_DELAY = TimeUnit.HOURS.toMillis(1);

    private final EventPublisher eventPublisher;
    private final ServiceManager serviceManager;
    private final JiraPropertySetFactory propertySetFactory;

    @GuardedBy("this")
    private final Set<LifecycleEvent> lifecycleEvents = EnumSet.noneOf(LifecycleEvent.class);

    public SchedulerLauncher(final EventPublisher eventPublisher, final ServiceManager serviceManager, final JiraPropertySetFactory jiraPropertySetFactory) {
        this.eventPublisher = eventPublisher;
        this.serviceManager = serviceManager;
        this.propertySetFactory = jiraPropertySetFactory;
    }

    @Override
    public void afterPropertiesSet() {
        registerListener();
        onLifecycleEvent(LifecycleEvent.AFTER_PROPERTIES_SET);
    }

    /**
     * This is received from SAL after the system is really up and running
     */
    @Override
    public void onStart() {
        onLifecycleEvent(LifecycleEvent.LIFECYCLE_AWARE_ON_START);
    }

    /**
     * It is not safe to use Active Objects before this event is received.
     */
    @EventListener
    public void onPluginEnabled(final PluginEnabledEvent event) {
        if (PLUGIN_KEY.equals(event.getPlugin().getKey())) {
            onLifecycleEvent(LifecycleEvent.PLUGIN_ENABLED);
        }
    }

    @Override
    public void destroy() throws Exception {
        unregisterListener();
    }

    /**
     * The latch which ensures all of the plugin/application lifecycle progress is completed before we call
     * {@code launch()}.
     */
    private void onLifecycleEvent(final LifecycleEvent event) {
        if (isLifecycleReady(event)) {
            unregisterListener();
            try {
                registerService();
            } catch (Exception ex) {
                logger.error("Unexpected error when starting JIRA SVN plugin." , ex);
            }
        }
    }

    /**
     * The event latch.
     * <p>
     * When something related to the plugin initialization happens, we call this with
     * the corresponding type of the event.  We will return {@code true} at most once, when the very last type
     * of event is triggered.  This method has to be {@code synchronized} because {@code EnumSet} is not
     * thread-safe and because we have multiple accesses to {@code lifecycleEvents} that need to happen
     * atomically for    correct behaviour.
     * </p>
     *
     * @param event the lifecycle event that occurred
     * @return {@code true} if this completes the set of initialization-related events; {@code false} otherwise
     */
    synchronized private boolean isLifecycleReady(final LifecycleEvent event) {
        return lifecycleEvents.add(event) && lifecycleEvents.size() == LifecycleEvent.values().length;
    }

    private void registerListener() {
        eventPublisher.register(this);
    }

    private void unregisterListener() {
        eventPublisher.unregister(this);
    }

    private void registerService() {

        try {
            if (serviceManager.getServiceWithName(UpdateSvnIndexService.SERVICE_NAME) == null) {
                serviceManager.addService(UpdateSvnIndexService.SERVICE_NAME, UpdateSvnIndexService.class, DEFAULT_SERVICE_DELAY);
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Service " + UpdateSvnIndexService.SERVICE_NAME + " registered");
            }
        } catch (Exception ex) {
            throw new RuntimeException("Unable to register service: " + UpdateSvnIndexService.SERVICE_NAME, ex);
        }
    }

    /**
     * Used to keep track of everything that needs to happen before we are sure that it is safe
     * to talk to all of the components we need to use, particularly the {@code SchedulerService}
     * We will not try to initialize until all of them have happened.
     */
    static enum LifecycleEvent {
        AFTER_PROPERTIES_SET,
        PLUGIN_ENABLED,
        LIFECYCLE_AWARE_ON_START
    }

}
