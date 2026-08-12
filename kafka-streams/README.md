```sh
# Build the project
mvn clean package

# Generate fake data
java -cp target/kafka-streams-*-run.jar it.fvaleri.kafka.Data

# Run the application (Ctrl+C to stop)
java -jar target/kafka-streams-*-run.jar

# Check the output
bin/kafka-topics.sh --bootstrap-server :9092 --list
bin/kafka-consumer-groups.sh --bootstrap-server :9092 --describe --group clicks
bin/kafka-console-consumer.sh --bootstrap-server :9092 --topic clicks.user.activity --from-beginning

# Cleanup
bin/kafka-streams-application-reset.sh --bootstrap-server :9092 --application-id clicks \
  --input-topics clicks.user.profile,clicks.pages.views,clicks.search
bin/kafka-topics.sh --bootstrap-server :9092 --delete --topic clicks.user.activity
```
