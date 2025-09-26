```sh
### run on localhost
export BOOTSTRAP_SERVERS="localhost:9092" \
       REGISTRY_URL="http://localhost:8080/apis/registry/v2" \
       ARTIFACT_GROUP="default" \
       TOPIC_NAME="my-topic"

# register the message schema
# you can automatically do that at build time enabling the plugin
curl -s -X POST -H "Content-Type: application/json" \
  -H "X-Registry-ArtifactId: my-topic-value" -H "X-Registry-ArtifactType: AVRO" \
  -d @src/main/resources/greeting.avsc \
  "$REGISTRY_URL/groups/default/artifacts?ifExists=RETURN_OR_UPDATE" | yq -Pj
{
  "name": "Greeting",
  "createdBy": "",
  "createdOn": "2022-09-30T06:31:36+0000",
  "modifiedBy": "",
  "modifiedOn": "2022-09-30T06:31:36+0000",
  "id": "my-topic-value",
  "version": "1",
  "type": "AVRO",
  "globalId": 4,
  "state": "ENABLED",
  "contentId": 6
}

mvn compile exec:java -q

### run on Kubernetes
mvn clean package

docker build -t ghcr.io/fvaleri/kafka-avro:latest .
echo $TOKEN | docker login ghcr.io -u fvaleri --password-stdin
docker push ghcr.io/fvaleri/kafka-avro:latest

export BOOTSTRAP_SERVERS=$(kubectl get k my-cluster -o yaml | yq '.status.listeners.[] | select(.name == "external").bootstrapServers') \
  REGISTRY_URL=http://$(kubectl get apicurioregistries my-schema-registry -o jsonpath="{.status.info.host}")/apis/registry/v2 \
  ARTIFACT_GROUP="default" \
  TOPIC_NAME="my-topic"

# register the message schema
curl -s -X POST -H "Content-Type: application/json" \
  -H "X-Registry-ArtifactId: my-topic-value" -H "X-Registry-ArtifactType: AVRO" \
  -d @src/main/resources/greeting.avsc \
  "$REGISTRY_URL/groups/default/artifacts?ifExists=RETURN_OR_UPDATE" | yq -Pj
{
  "name": "Greeting",
  "createdBy": "",
  "createdOn": "2022-09-30T06:31:36+0000",
  "modifiedBy": "",
  "modifiedOn": "2022-09-30T06:31:36+0000",
  "id": "my-topic-value",
  "version": "1",
  "type": "AVRO",
  "globalId": 4,
  "state": "ENABLED",
  "contentId": 6
}

envsubst < install.yaml | kubectl create -f -
kubectl logs -f $(kubectl get po -l app=kafka-avro -o name)
```
