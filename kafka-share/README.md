```sh
# enable the feature
echo "group.coordinator.rebalance.protocols=classic,consumer,share" >> config/server.properties
bin/kafka-features.sh --bootstrap-server :9092 upgrade --feature share.version=1

# create a test topic and send some messages
bin/kafka-topics.sh --bootstrap-server :9092 --create --topic my-topic --partitions 1

# run the example in two different shells
mvn compile exec:java -q

# check share groups
bin/kafka-share-groups.sh --bootstrap-server :9092 --describe --group my-share-group
bin/kafka-share-groups.sh --bootstrap-server :9092 --describe --group my-share-group --members

# send some messages
bin/kafka-producer-perf-test.sh --producer-props bootstrap.servers=:9092 linger.ms=0 batch.size=10000 \
  --topic my-topic --throughput 5 --payload-monotonic --num-records 1000
```
