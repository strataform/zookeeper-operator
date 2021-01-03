package com.github.strataform.operator.zookeeper;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapKeySelector;
import io.fabric8.kubernetes.api.model.EnvVarSource;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.DoneableStatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEvent;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller(crdName = "zookeepers.zookeeper.strataform.github.com")
public class ZookeeperController implements ResourceController<Zookeeper> {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final KubernetesClient kubernetesClient;

    public ZookeeperController(KubernetesClient client) {
        this.kubernetesClient = client;
    }

    @Override
    public void init(EventSourceManager eventSourceManager) {
        StatefulSetEventSource statefulSetEventSource = StatefulSetEventSource.createAndRegisterWatch(kubernetesClient);
        eventSourceManager.registerEventSource("statefulset-event-source", statefulSetEventSource);
    }

    @Override
    public UpdateControl<Zookeeper> createOrUpdateResource(Zookeeper zookeeper, Context<Zookeeper> context) {
        Optional<CustomResourceEvent> latestCREvent = context.getEvents().getLatestOfType(CustomResourceEvent.class);
        if (latestCREvent.isPresent()) {
            createOrUpdateConfigMap(zookeeper);
            createOrUpdateServices(zookeeper);
            createOrUpdateStatefulSet(zookeeper);
        }

        Optional<StatefulSetEvent> latestStatefulSetEvent = context.getEvents().getLatestOfType(StatefulSetEvent.class);
        if (latestStatefulSetEvent.isPresent()) {
            Zookeeper updatedZookeeper = updateEnsembleStatus(zookeeper, latestStatefulSetEvent.get().getStatefulSet());
            log.info(
                    "Updating status of ZookeeperEnsemble {} in namespace {} to {} ready replicas",
                    zookeeper.getMetadata().getName(),
                    zookeeper.getMetadata().getNamespace(),
                    zookeeper.getStatus().getReadyReplicas());
            return UpdateControl.updateStatusSubResource(updatedZookeeper);
        }

        return UpdateControl.noUpdate();
    }

    private void createOrUpdateConfigMap(Zookeeper zookeeper) {
        String namespace = zookeeper.getMetadata().getNamespace();

        ConfigMap configMap = loadYaml(ConfigMap.class, "configmap.yaml");
        configMap.getMetadata().setNamespace(namespace);
        configMap.getMetadata().setName(zookeeper.getMetadata().getName() + "-config");
        configMap.getMetadata().getLabels().put("created-by", zookeeper.getMetadata().getName());
        configMap.getMetadata().getLabels().put("managed-by", "zookeeper-operator");

        StringBuilder dynamicConfigFile = new StringBuilder();
        var replicas = zookeeper.getSpec().getReplicas();
        for (var replica = 0; replica < replicas; replica++) {
            var serverId = replica + 1;
            dynamicConfigFile.append(
                    MessageFormat.format("server.{0}=zookeeper-{1}.{2}-headless.{3}.svc.cluster.local:2888:3888\n",
                            serverId,
                            replica,
                            zookeeper.getMetadata().getName(),
                            zookeeper.getMetadata().getNamespace())
            );
        }
        var data = configMap.getData();
        data.put("zoo.cfg.dynamic", dynamicConfigFile.toString());
        configMap.setData(data);

        OwnerReference ownerReference = configMap.getMetadata().getOwnerReferences().get(0);
        ownerReference.setName(zookeeper.getMetadata().getName());
        ownerReference.setUid(zookeeper.getMetadata().getUid());

        log.info("Creating or updating ConfigMap {} in {}", configMap.getMetadata().getName(), namespace);
        kubernetesClient.configMaps().inNamespace(namespace).createOrReplace(configMap);
    }

    private void createOrUpdateServices(Zookeeper zookeeper) {
        String namespace = zookeeper.getMetadata().getNamespace();

        Service headless = loadYaml(Service.class, "headless.yaml");
        headless.getMetadata().setNamespace(namespace);
        headless.getMetadata().setName(zookeeper.getMetadata().getName() + "-headless");
        headless.getMetadata().getLabels().put("created-by", zookeeper.getMetadata().getName());
        headless.getMetadata().getLabels().put("managed-by", "zookeeper-operator");
        headless.getSpec().getSelector().put("app", zookeeper.getMetadata().getName());

        OwnerReference ownerReference = headless.getMetadata().getOwnerReferences().get(0);
        ownerReference.setName(zookeeper.getMetadata().getName());
        ownerReference.setUid(zookeeper.getMetadata().getUid());

        log.info("Creating or updating Service {} in {}", headless.getMetadata().getName(), namespace);
        kubernetesClient.services().inNamespace(namespace).createOrReplace(headless);

        // Repeats...
        Service service = loadYaml(Service.class, "service.yaml");
        service.getMetadata().setNamespace(namespace);
        service.getMetadata().setName(zookeeper.getMetadata().getName() + "-service");
        service.getMetadata().getLabels().put("created-by", zookeeper.getMetadata().getName());
        service.getMetadata().getLabels().put("managed-by", "zookeeper-operator");
        service.getSpec().getSelector().put("app", zookeeper.getMetadata().getName());

        ownerReference = service.getMetadata().getOwnerReferences().get(0);
        ownerReference.setName(zookeeper.getMetadata().getName());
        ownerReference.setUid(zookeeper.getMetadata().getUid());

        log.info("Creating or updating Service {} in {}", service.getMetadata().getName(), namespace);
        kubernetesClient.services().inNamespace(namespace).createOrReplace(service);
    }

    private void createOrUpdateStatefulSet(Zookeeper zookeeper) {
        String namespace = zookeeper.getMetadata().getNamespace();

        StatefulSet existingStatefulSet = kubernetesClient
                .apps()
                .statefulSets()
                .inNamespace(namespace)
                .withName(zookeeper.getMetadata().getName())
                .get();
        if (existingStatefulSet == null) {
            StatefulSet statefulSet = loadYaml(StatefulSet.class, "statefulset.yaml");
            statefulSet.getMetadata().setNamespace(namespace);
            statefulSet.getMetadata().setName(zookeeper.getMetadata().getName());
            statefulSet.getMetadata().getLabels().put("created-by", zookeeper.getMetadata().getName());
            statefulSet.getMetadata().getLabels().put("managed-by", "zookeeper-operator");
            statefulSet
                    .getSpec()
                    .getTemplate()
                    .getSpec()
                    .getContainers()
                    .get(0)
                    .setName(zookeeper.getMetadata().getName());
            statefulSet
                    .getSpec()
                    .getTemplate()
                    .getSpec()
                    .getContainers()
                    .get(0)
                    .setImage(zookeeper.getSpec().getImage());
            var keySelector = new ConfigMapKeySelector("java_options", zookeeper.getMetadata().getName() + "-config",
                    false);
            var envVar = new EnvVarSource();
            envVar.setConfigMapKeyRef(keySelector);
            statefulSet
                    .getSpec()
                    .getTemplate()
                    .getSpec()
                    .getContainers()
                    .get(0)
                    .getEnv()
                    .get(0)
                    .setValueFrom(envVar);
            statefulSet.getSpec().setReplicas(zookeeper.getSpec().getReplicas());
            statefulSet.getSpec().setServiceName(zookeeper.getMetadata().getName() + "-headless");

            // Make sure label selector matches label (which has to be matched by service selector too).
            statefulSet
                    .getSpec()
                    .getTemplate()
                    .getMetadata()
                    .getLabels()
                    .put("app", zookeeper.getMetadata().getName());
            statefulSet
                    .getSpec()
                    .getSelector()
                    .getMatchLabels()
                    .put("app", zookeeper.getMetadata().getName());

            OwnerReference ownerReference = statefulSet.getMetadata().getOwnerReferences().get(0);
            ownerReference.setName(zookeeper.getMetadata().getName());
            ownerReference.setUid(zookeeper.getMetadata().getUid());

            log.info("Creating or updating StatefulSet {} in {}", statefulSet.getMetadata().getName(), namespace);
            kubernetesClient.apps().statefulSets().inNamespace(namespace).create(statefulSet);
        } else {
            existingStatefulSet
                    .getSpec()
                    .getTemplate()
                    .getSpec()
                    .getContainers()
                    .get(0)
                    .setImage(zookeeper.getSpec().getImage());
            existingStatefulSet.getSpec().setReplicas(zookeeper.getSpec().getReplicas());

            log.info("Creating or updating StatefulSet {} in {}", existingStatefulSet.getMetadata().getName(),
                    namespace);
            kubernetesClient.apps().statefulSets().inNamespace(namespace).createOrReplace(existingStatefulSet);
        }
    }

    private Zookeeper updateEnsembleStatus(Zookeeper zookeeper, StatefulSet statefulSet) {
        var statefulSetStatus = Objects.requireNonNullElse(statefulSet.getStatus(), new StatefulSetStatus());
        int readyReplicas = Objects.requireNonNullElse(statefulSetStatus.getReadyReplicas(), 0);
        ZookeeperStatus status = new ZookeeperStatus();
        status.setReadyReplicas(readyReplicas);
        zookeeper.setStatus(status);
        return zookeeper;
    }

    @Override
    public DeleteControl deleteResource(Zookeeper zookeeper, Context<Zookeeper> context) {
        deleteStatefulSet(zookeeper);
        deleteServices(zookeeper);
        deleteConfigMap(zookeeper);

        return DeleteControl.DEFAULT_DELETE;
    }

    private void deleteStatefulSet(Zookeeper zookeeper) {
        RollableScalableResource<StatefulSet, DoneableStatefulSet> statefulSet = kubernetesClient
                .apps()
                .statefulSets()
                .inNamespace(zookeeper.getMetadata().getNamespace())
                .withName(zookeeper.getMetadata().getName());
        if (statefulSet.get() != null) {
            var name = statefulSet.get().getMetadata().getName();
            var namespace = zookeeper.getMetadata().getNamespace();
            log.info("Deleting StatefulSet {} in {}", name, namespace);
            statefulSet.delete();
        }
    }

    private void deleteServices(Zookeeper zookeeper) {
        var service = kubernetesClient
                .services()
                .inNamespace(zookeeper.getMetadata().getNamespace())
                .withName(zookeeper.getMetadata().getName() + "-service");
        if (service.get() != null) {
            var name = service.get().getMetadata().getName();
            var namespace = zookeeper.getMetadata().getNamespace();
            log.info("Deleting Service {} in {}", name, namespace);
            service.delete();
        }

        var headless = kubernetesClient
                .services()
                .inNamespace(zookeeper.getMetadata().getNamespace())
                .withName(zookeeper.getMetadata().getName() + "-headless");
        if (headless.get() != null) {
            var name = headless.get().getMetadata().getName();
            var namespace = zookeeper.getMetadata().getNamespace();
            log.info("Deleting Service {} in {}", name, namespace);
            headless.delete();
        }
    }

    private void deleteConfigMap(Zookeeper zookeeper) {
        var configMap = kubernetesClient
                .configMaps()
                .inNamespace(zookeeper.getMetadata().getNamespace())
                .withName(zookeeper.getMetadata().getName() + "-config");
        if (configMap.get() != null) {
            var name = configMap.get().getMetadata().getName();
            var namespace = zookeeper.getMetadata().getNamespace();
            log.info("Deleting ConfigMap {} in {}", name, namespace);
            configMap.delete();
        }
    }

    private <T> T loadYaml(Class<T> clazz, String yaml) {
        try (InputStream is = getClass().getResourceAsStream(yaml)) {
            return Serialization.unmarshal(is, clazz);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot find yaml on classpath: " + yaml);
        }
    }
}
