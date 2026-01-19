package it.fvaleri.kafka;

import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.config.SerdeConfig;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private static String bootstrapServers;
    private static String registryUrl;
    private static String topicName;

    static {
        if (System.getenv("BOOTSTRAP_SERVERS") != null) {
            bootstrapServers = System.getenv("BOOTSTRAP_SERVERS");
        }
        if (System.getenv("REGISTRY_URL") != null) {
            registryUrl = System.getenv("REGISTRY_URL");
        }
        if (System.getenv("TOPIC_NAME") != null) {
            topicName = System.getenv("TOPIC_NAME");
        }
    }

    public static void main(String[] args) {
        try (var producer = createKafkaProducer();
             var consumer = createKafkaConsumer()) {
            createTopics(topicName);
            Schema schema = loadSchemaFromFile("greeting-v1.avsc");

            LOG.info("Producing records");
            for (int i = 0; i < 5; i++) {
                // we use the generic record instead of generating classes from the schema
                GenericRecord record = new GenericData.Record(schema);
                record.put("Message", "Hello");
                record.put("Time", System.currentTimeMillis());
                producer.send(new ProducerRecord<>(topicName, null, record));
            }

            LOG.info("Consuming records");
            consumer.subscribe(Set.of(topicName));
            // the deserializer extracts the globalId from payload and uses it to look up the schema
            ConsumerRecords<String, GenericRecord> records = consumer.poll(Duration.ofSeconds(5));
            records.forEach(record
                    -> LOG.info("Record: {}-{}", record.value().get("Message"), record.value().get("Time")));
        } catch (Throwable e) {
            LOG.error("Unexpected error: {}", e.getMessage(), e);
        }
    }

    private static KafkaProducer<String, GenericRecord> createKafkaProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "client-" + UUID.randomUUID());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroKafkaSerializer.class.getName());
        props.put(SerdeConfig.REGISTRY_URL, registryUrl);
        // this configures the schema cache eviction period
        props.putIfAbsent(SerdeConfig.CHECK_PERIOD_MS, 30_000);
        return new KafkaProducer<>(props);
    }

    private static KafkaConsumer<String, GenericRecord> createKafkaConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "client-" + UUID.randomUUID());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "my-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, AvroKafkaDeserializer.class.getName());
        props.put(SerdeConfig.REGISTRY_URL, registryUrl);
        // this configures the schema cache eviction period
        props.putIfAbsent(SerdeConfig.CHECK_PERIOD_MS, 30_000);
        return new KafkaConsumer<>(props);
    }

    private static void createTopics(String... topicNames) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.CLIENT_ID_CONFIG, "client" + UUID.randomUUID());
        try (Admin admin = Admin.create(props)) {
            // use default RF to avoid NOT_ENOUGH_REPLICAS error with minISR>1
            short replicationFactor = -1;
            List<NewTopic> newTopics = Arrays.stream(topicNames)
                .map(name -> new NewTopic(name, -1, replicationFactor))
                .collect(Collectors.toList());
            try {
                admin.createTopics(newTopics).all().get();
                LOG.info("Created topics: {}", Arrays.toString(topicNames));
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof TopicExistsException)) {
                    throw e;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private static Schema loadSchemaFromFile(String fileName) {
        LOG.info("Loading schema from file: {}", fileName);
        try (InputStream inputStream = Main.class.getClassLoader().getResourceAsStream(fileName)) {
            if (inputStream == null) {
                LOG.error("Schema file not found: {}", fileName);
                System.exit(1);
            }
            return new Schema.Parser().parse(inputStream);
        } catch (IOException e) {
            LOG.error("Error loading schema from file: {}", e.getMessage(), e);
            System.exit(1);
        }
        return null;
    }
}
