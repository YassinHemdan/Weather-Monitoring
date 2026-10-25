#!/bin/sh

docker exec -it kafka-container kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic raining-events-topic \
  --from-beginning