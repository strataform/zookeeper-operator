# zookeeper-operator

Kubernetes operator for Apache Zookeeper.

## Deploy Zookeeper

To deploy a basic Zookeeper ensemble without using the operator, run the following command:

```shell
kubectl apply -f deploy/zookeeper.yaml
```

To check that the ensemble is correctly deployed, run:

```shell
kubectl exec zookeeper-0 -- bin/zkCli.sh create /hello world
```

Then, make sure we can read the data back:

```shell
kubectl exec zookeeper-2 -- bin/zkCli.sh get /hello
```
