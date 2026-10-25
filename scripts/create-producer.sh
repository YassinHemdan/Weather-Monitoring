#!/bin/sh

docker exec -it kafka-container kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic demo