```sh
mvn package -DskipTests
cp target/kafka-smt-*.jar $PLUGINS

# add to the connectors SMT chain
"transforms": "JsonWriter",
"transforms.JsonWriter.type": "it.fvaleri.kafka.JsonWriter"
```
