#!/bin/sh

docker exec -it kafka-container kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic weather-events-topic \
  --from-beginning