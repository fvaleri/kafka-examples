```sh
# run on localhost
export BOOTSTRAP_SERVERS="localhost:9092" \
       INSTANCE_ID="kafka-txn-0" \
       GROUP_ID="my-group" \
       INPUT_TOPIC="input-topic" \
       OUTPUT_TOPIC="output-topic" 
mvn compile exec:java

bin/kafka-console-producer.sh --bootstrap-server :9092 --topic input-topic
bin/kafka-console-consumer.sh --bootstrap-server :9092 --topic output-topic --from-beginning

# run on Kubernetes
mvn package

docker build -t ghcr.io/fvaleri/kafka-txn:latest .
echo $TOKEN | docker login ghcr.io -u fvaleri --password-stdin
docker push ghcr.io/fvaleri/kafka-txn:latest

kubectl create -f install.yaml
kubectl exec -it my-cluster-broker-5 -- bin/kafka-console-producer.sh \
  --bootstrap-server my-cluster-kafka-bootstrap:9092 --topic input-topic
kubectl exec my-cluster-broker-5 -- bin/kafka-console-consumer.sh \
  --bootstrap-server my-cluster-kafka-bootstrap:9092 --topic output-topic --from-beginning
```
