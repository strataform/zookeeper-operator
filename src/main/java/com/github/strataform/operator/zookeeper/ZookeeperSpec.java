package com.github.strataform.operator.zookeeper;

public class ZookeeperSpec {

    private String image;
    private Integer replicas;

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Integer getReplicas() {
        return replicas;
    }

    public void setReplicas(Integer replicas) {
        this.replicas = replicas;
    }
}
