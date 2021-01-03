package com.github.strataform.operator.zookeeper;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;
import static java.net.HttpURLConnection.HTTP_GONE;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.javaoperatorsdk.operator.processing.event.AbstractEventSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatefulSetEventSource extends AbstractEventSource implements Watcher<StatefulSet> {

    private static final Logger log = LoggerFactory.getLogger(StatefulSetEventSource.class);

    private final KubernetesClient client;

    public static StatefulSetEventSource createAndRegisterWatch(KubernetesClient client) {
        StatefulSetEventSource statefulSetEventSource = new StatefulSetEventSource(client);
        statefulSetEventSource.registerWatch();
        return statefulSetEventSource;
    }

    private StatefulSetEventSource(KubernetesClient client) {
        this.client = client;
    }

    private void registerWatch() {
        client.apps().statefulSets().inAnyNamespace().withLabel("managed-by", "zookeeper-operator").watch(this);
    }

    @Override
    public void eventReceived(Action action, StatefulSet statefulSet) {
        log.info(
                "Event received for action: {}, StatefulSet: {} (rr={})",
                action.name(),
                statefulSet.getMetadata().getName(),
                statefulSet.getStatus().getReadyReplicas());

        if (action == Action.ERROR) {
            log.warn(
                    "Skipping {} event for custom resource uid: {}, version: {}",
                    action,
                    getUID(statefulSet),
                    getVersion(statefulSet));
            return;
        }

        eventHandler.handleEvent(new StatefulSetEvent(action, statefulSet, this));
    }

    @Override
    public void onClose(KubernetesClientException e) {
        if (e == null) {
            return;
        }
        if (e.getCode() == HTTP_GONE) {
            log.warn("Received error for watch, will try to reconnect.", e);
            registerWatch();
        } else {
            // Note that this should not happen normally, since fabric8 client handles reconnect.
            // In case it tries to reconnect this method is not called.
            log.error("Unexpected error happened with watch. Will exit.", e);
            System.exit(1);
        }
    }
}
