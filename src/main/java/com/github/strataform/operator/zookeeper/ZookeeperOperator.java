package com.github.strataform.operator.zookeeper;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javalin.Javalin;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZookeeperOperator {

    private static final Logger log = LoggerFactory.getLogger(ZookeeperOperator.class);

    public static void main(String[] args) {

        log.info("Zookeeper Operator starting");

        Config config = new ConfigBuilder().withNamespace(null).build();
        KubernetesClient client = new DefaultKubernetesClient(config);
        Operator operator = new Operator(client, DefaultConfigurationService.instance());

        ZookeeperController zookeeperController = new ZookeeperController(client);
        operator.registerControllerForAllNamespaces(zookeeperController);

        Javalin app = Javalin.create().start(8080);
        app.get("/health", ctx -> ctx.result("ALL GOOD!"));
    }
}
