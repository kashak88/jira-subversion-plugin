package com.atlassian.jira.plugin.ext.subversion.revisions.scheduling.clustersafe;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.service.ServiceManager;
import com.atlassian.plugin.event.events.PluginUninstalledEvent;
import org.springframework.beans.factory.InitializingBean;

/**
 *
 * @since v2.1
 */
public class PreUninstallCleanup implements InitializingBean {

    private final EventPublisher eventPublisher;
    private ServiceManager serviceManager;

    public PreUninstallCleanup(final EventPublisher eventPublisher, final ServiceManager serviceManager) {
        this.eventPublisher = eventPublisher;
        this.serviceManager = serviceManager;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        eventPublisher.register(this);
    }

    @EventListener
    public void onPluginUninstalled(final PluginUninstalledEvent event) {
        if (event.getPlugin().getKey().equals(SchedulerLauncher.PLUGIN_KEY)) {
            try {
                serviceManager.removeServiceByName(UpdateSvnIndexService.SERVICE_NAME);
            } catch (Exception e) {
                throw new RuntimeException("Exception occurred while uninstalling " + UpdateSvnIndexService.SERVICE_NAME, e);
            }
        }
    }
}
