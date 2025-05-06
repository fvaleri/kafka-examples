```sh
# run on localhost
NUM_MESSAGES="100" mvn compile exec:java
CLIENT_TYPE="consumer" NUM_MESSAGES="100" mvn compile exec:java

# run on Kubernetes
mvn package

docker build -t ghcr.io/fvaleri/kafka-client:latest .
echo $TOKEN | docker login ghcr.io -u fvaleri --password-stdin
docker push ghcr.io/fvaleri/kafka-client:latest

kubectl create -f install.yaml
kubectl logs -f $(kubectl get po -l app=my-producer -o name)
kubectl logs -f $(kubectl get po -l app=my-consumer -o name)
```
