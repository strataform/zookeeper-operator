package com.github.strataform.operator.zookeeper;

import io.fabric8.kubernetes.client.CustomResource;

public class Zookeeper extends CustomResource {

    private ZookeeperSpec spec;
    private ZookeeperStatus status;

    public ZookeeperSpec getSpec() {
        if (spec == null) {
            spec = new ZookeeperSpec();
        }
        return spec;
    }

    public void setSpec(ZookeeperSpec spec) {
        this.spec = spec;
    }

    public ZookeeperStatus getStatus() {
        if (status == null) {
            status = new ZookeeperStatus();
        }
        return status;
    }

    public void setStatus(ZookeeperStatus status) {
        this.status = status;
    }
}
