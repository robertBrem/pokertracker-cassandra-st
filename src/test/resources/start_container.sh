#!/usr/bin/env bash

VERSION=0.0.1

echo "stop and rm keycloak"
docker stop keycloak && docker rm keycloak

echo "start keycloak"
# -e KEYCLOAK_USER=admin -e KEYCLOAK_PASSWORD=admin \
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

echo "copy files for pokertracker"
rm -r pokertracker-docker
mkdir pokertracker-docker
cp pokertracker.war pokertracker-docker
cp Dockerfile pokertracker-docker
cp standalone.xml pokertracker-docker

echo "build image"
docker build -t registry:5000/robertbrem/pokertracker:$VERSION pokertracker-docker
docker stop pokertracker && docker rm pokertracker
docker run -d -p 8282:8080 --name pokertracker -e CASSANDRA_IP=`ip addr show docker0 | grep "scope global" | awk '{print $2}' | sed 's/\/.*//'` registry:5000/robertbrem/pokertracker:$VERSION
echo "Docker container running with name pokertracker"

echo " wait for wildfly to start"
while [ $(curl --write-out %{http_code} --silent --output /dev/null http://localhost:8282/pokertracker/resources/health) -ne "200" ]
do
 echo "$(date) - still trying"
 sleep 1
done
echo "$(date) - connected successfully"