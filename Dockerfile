FROM registry.access.redhat.com/ubi8/ubi:8.3 AS build

COPY . /src

WORKDIR /src

RUN yum install -y java-11-openjdk-devel \
    && ./gradlew installDist

FROM registry.access.redhat.com/ubi8/ubi:8.3

RUN yum install -y java-11-openjdk

COPY --from=build /src/build/install/zookeeper-operator /opt/operator

WORKDIR /opt/operator

EXPOSE 8080

ENTRYPOINT ["sh", "/opt/operator/bin/zookeeper-operator"]
