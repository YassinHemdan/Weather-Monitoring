#!/bin/sh

docker exec kafka-container kafka-topics \
  --bootstrap-server localhost:9092 \
  --create \
  --if-not-exists \
  --replication-factor 1 \
  --partitions 1 \
  --topic weather-events-topic

docker exec kafka-container kafka-topics \
  --bootstrap-server localhost:9092 \
  --create \
  --if-not-exists \
  --replication-factor 1 \
  --partitions 1 \
  --topic raining-events-topic