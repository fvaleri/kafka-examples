```sh
# Create a test topic and send some messages
bin/kafka-topics.sh --bootstrap-server :9092 --create --topic my-topic --partitions 1

# Run the example
mvn compile exec:java

# Check share groups
bin/kafka-share-groups.sh --bootstrap-server :9092 --describe --group my-share-group
bin/kafka-share-groups.sh --bootstrap-server :9092 --describe --group my-share-group --members

# Send some messages
bin/kafka-producer-perf-test.sh --command-property bootstrap.servers=:9092 linger.ms=0 batch.size=10000 \
  --topic my-topic --throughput 5 --payload-monotonic --num-records 1000
```
