#!/usr/bin/env bash

VERSION=0.0.1

echo "stop and rm keycloak"
docker stop keycloak && docker rm keycloak

# -e KEYCLOAK_USER=admin -e KEYCLOAK_PASSWORD=admin \
echo "start keycloak"
docker run --name keycloak -d \
-v /root/volumes/keycloakdata:/opt/jboss/keycloak/standalone/data \
-p 8088:8080 -p 8443:8443 robertbrem/keycloak:1.9.5

echo "stop and rm cassandra"
docker stop cassandra && docker rm cassandra

echo "start cassandra"
docker run --name cassandra \
-d -e CASSANDRA_START_RPC=true \
-p 9160:9160 -p 9042:9042 -p 7199:7199 -p 7001:7001 -p 7000:7000 cassandra

echo "wait for cassandra to start"
while ! docker logs cassandra | grep "Listening for thrift clients..."
do
 echo "$(date) - still trying"
 sleep 1
done
echo "$(date) - connected successfully"

echo "copy init script in container"
docker cp initial_db.sql cassandra:/

echo "create database"
docker exec -d cassandra cqlsh localhost -f /initial_db.sql

echo "stop and rm kafka"
docker stop kafka && docker rm kafka

echo "stop and rm zookeeper"
docker stop zookeeper && docker rm zookeeper

echo "start zookeeper"
docker run --name zookeeper -d \
-p 2181:2181 \
-e ZOOKEEPER_ID="1" \
-e ZOOKEEPER_SERVER_1=kafka-zoo-svc \
digitalwonderland/zookeeper

echo "start kafka"
docker run --name kafka -d -p 9092:9092 \
-e ENABLE_AUTO_EXTEND="true" \
-e KAFKA_RESERVED_BROKER_MAX_ID="999999999" \
-e KAFKA_AUTO_CREATE_TOPICS_ENABLE="false" \
-e KAFKA_PORT="9092" \
-e KAFKA_ADVERTISED_PORT="9092" \
-e KAFKA_CREATE_TOPICS="pokertracker:1:1" \
-e KAFKA_ADVERTISED_HOST_NAME=`ip addr show docker0 | grep "scope global" | awk '{print $2}' | sed 's/\/.*//'` \
-e KAFKA_ZOOKEEPER_CONNECT=`ip addr show docker0 | grep "scope global" | awk '{print $2}' | sed 's/\/.*//'`:2181 \
cloudtrackinc/kubernetes-kafka

echo "copy files for keycloak"
rm -r wildfly-keycloak-docker
mkdir wildfly-keycloak-docker
cp keycloak/Dockerfile wildfly-keycloak-docker
cp keycloak/standalone.xml wildfly-keycloak-docker

echo "build image wildfly-keycloak"
docker build -t registry:5000/robertbrem/wildfly-keycloak wildfly-keycloak-docker

echo "copy files for pokertracker"
rm -r pokertracker-docker
mkdir pokertracker-docker
cp pokertracker-command/pokertracker.war pokertracker-docker
cp pokertracker-command/Dockerfile pokertracker-docker

echo "build image pokertracker-command"
docker build -t registry:5000/robertbrem/pokertracker:$VERSION pokertracker-docker
docker stop pokertracker && docker rm pokertracker
docker run -d -p 8282:8080 --name pokertracker \
-e CASSANDRA_ADDRESS=`ip addr show docker0 | grep "scope global" | awk '{print $2}' | sed 's/\/.*//'` \
-e KAFKA_ADDRESS=`ip addr show docker0 | grep "scope global" | awk '{print $2}' | sed 's/\/.*//'`:9092 \
registry:5000/robertbrem/pokertracker:$VERSION
echo "Docker container running with name pokertracker"

echo "copy files for pokertracker-query"
rm -r pokertracker-query-docker
mkdir pokertracker-query-docker
cp pokertracker-query/pokertracker-query.war pokertracker-query-docker
cp pokertracker-query/Dockerfile pokertracker-query-docker

echo "build image pokertracker-query"
docker build -t registry:5000/robertbrem/pokertracker-query:$VERSION pokertracker-query-docker
docker stop pokertracker-query && docker rm pokertracker-query
docker run -d -p 8383:8080 --name pokertracker-query \
-e KAFKA_ADDRESS=`ip addr show docker0 | grep "scope global" | awk '{print $2}' | sed 's/\/.*//'`:9092 \
registry:5000/robertbrem/pokertracker-query:$VERSION
echo "Docker container running with name pokertracker-query"

echo " wait for pokertracker to start"
while [ $(curl --write-out %{http_code} --silent --output /dev/null http://localhost:8282/pokertracker/resources/health) -ne "200" ]
do
 echo "$(date) - still trying"
 sleep 1
done
echo "$(date) - connected to pokertracker successfully"

echo " wait for pokertracker-query to start"
while [ $(curl --write-out %{http_code} --silent --output /dev/null http://localhost:8383/pokertracker-query/resources/health) -ne "200" ]
do
 echo "$(date) - still trying"
 sleep 1
done
echo "$(date) - connected to pokertracker-query successfully"