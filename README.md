# Zookeeper operator

Kubernetes operator for Zookeeper.

## Deploy Zookeeper

To deploy a basic Zookeeper ensemble using the operator, run the following commands:

```shell
kubectl apply -f deploy/operator/crd.yaml
kubectl apply -f deploy/operator/operator.yaml
kubectl apply -f deploy/zookeeper/zookeeper.yaml
```

To check that the zookeeper is correctly deployed, run:

```shell
kubectl exec zookeeper-0 -- bin/zkCli.sh create /hello world
```

Then, make sure we can read the data back:

```shell
kubectl exec zookeeper-2 -- bin/zkCli.sh get /hello
```

### Developing manifests

Sometimes to facilitate development it is useful to deploy an ensemble without using the operator.
To deploy a basic Zookeeper ensemble without using the operator, run the following command:

```shell
kubectl apply -f deploy/manifests/zookeeper.yaml
```
