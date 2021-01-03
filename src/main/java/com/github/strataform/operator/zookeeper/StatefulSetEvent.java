package com.github.strataform.operator.zookeeper;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.Watcher;
import io.javaoperatorsdk.operator.processing.event.AbstractEvent;

public class StatefulSetEvent extends AbstractEvent {

    private final Watcher.Action action;
    private final StatefulSet statefulSet;

    public StatefulSetEvent(
            Watcher.Action action,
            StatefulSet statefulSet,
            StatefulSetEventSource statefulSetEventSource
    ) {
        // TODO: this mapping is really critical and should be made more explicit
        super(statefulSet.getMetadata().getOwnerReferences().get(0).getUid(), statefulSetEventSource);
        this.action = action;
        this.statefulSet = statefulSet;
    }

    public Watcher.Action getAction() {
        return action;
    }

    public StatefulSet getStatefulSet() {
        return statefulSet;
    }

    public String resourceUid() {
        return getStatefulSet().getMetadata().getUid();
    }

    @Override
    public String toString() {
        return "CustomResourceEvent{"
                + "action="
                + action
                + ", resource=[ name="
                + getStatefulSet().getMetadata().getName()
                + ", kind="
                + getStatefulSet().getKind()
                + ", apiVersion="
                + getStatefulSet().getApiVersion()
                + " ,resourceVersion="
                + getStatefulSet().getMetadata().getResourceVersion()
                + ", markedForDeletion: "
                + (getStatefulSet().getMetadata().getDeletionTimestamp() != null
                && !getStatefulSet().getMetadata().getDeletionTimestamp().isEmpty())
                + " ]"
                + '}';
    }
}
