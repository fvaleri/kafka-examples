```sh
### run on localhost
curl -sLk https://repo1.maven.org/maven2/io/apicurio/apicurio-registry-app/3.1.6/apicurio-registry-app-3.1.6-all.tar.gz \
  | tar -xzf - -C /tmp && nohup java -jar "/tmp/quarkus-app/quarkus-run.jar" >/dev/null 2>&1 &

export BOOTSTRAP_SERVERS="localhost:9092" \
  REGISTRY_URL="http://localhost:8080/apis/registry/v3" \
  TOPIC_NAME="my-topic"

# register the message schema
curl -s -X POST "$REGISTRY_URL"/groups/default/artifacts \
  -H "Content-Type: application/json" \
  -d @src/main/resources/my-topic-value.json | yq -o json
{
  "artifact": {
    "owner": "",
    "createdOn": "2026-01-20T09:18:16Z",
    "modifiedBy": "",
    "modifiedOn": "2026-01-20T09:18:16Z",
    "artifactType": "AVRO",
    "artifactId": "my-topic-value"
  },
  "version": {
    "version": "1",
    "owner": "",
    "createdOn": "2026-01-20T09:18:16Z",
    "artifactType": "AVRO",
    "globalId": 1,
    "state": "ENABLED",
    "contentId": 1,
    "artifactId": "my-topic-value",
    "modifiedBy": "",
    "modifiedOn": "2026-01-20T09:18:16Z"
  }
}

mvn compile exec:java -q

### run on Kubernetes
mvn clean package

docker build -t ghcr.io/fvaleri/kafka-avro:latest .
echo $TOKEN | docker login ghcr.io -u fvaleri --password-stdin
docker push ghcr.io/fvaleri/kafka-avro:latest

export BOOTSTRAP_SERVERS=$(kubectl get k my-cluster -o yaml | yq '.status.listeners.[] | select(.name == "external").bootstrapServers') \
  REGISTRY_URL=http://$(kubectl get apicurioregistries3 my-schema-registry -o jsonpath="{.spec.app.ingress.host}")/apis/registry/v3 \
  TOPIC_NAME="my-topic"

# register the message schema
curl -s -X POST "$REGISTRY_URL/groups/default/artifacts \
  -H "Content-Type: application/json" \
  -d @src/main/resources/my-topic-value.json | yq -o json
{
  "artifact": {
    "owner": "",
    "createdOn": "2026-01-20T09:18:16Z",
    "modifiedBy": "",
    "modifiedOn": "2026-01-20T09:18:16Z",
    "artifactType": "AVRO",
    "artifactId": "my-topic-value"
  },
  "version": {
    "version": "1",
    "owner": "",
    "createdOn": "2026-01-20T09:18:16Z",
    "artifactType": "AVRO",
    "globalId": 1,
    "state": "ENABLED",
    "contentId": 1,
    "artifactId": "my-topic-value",
    "modifiedBy": "",
    "modifiedOn": "2026-01-20T09:18:16Z"
  }
}

envsubst < install.yaml | kubectl create -f -
kubectl logs -f $(kubectl get po -l app=kafka-avro -o name)
```
