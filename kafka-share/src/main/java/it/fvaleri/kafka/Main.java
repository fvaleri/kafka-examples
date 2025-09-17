package it.fvaleri.kafka;

import org.apache.kafka.clients.consumer.AcknowledgeType;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaShareConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

import static java.util.Collections.singleton;

public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try (var consumer = createKafkaShareConsumer()) {
            // subscribe to a topic, joining the share group
            consumer.subscribe(singleton("my-topic"));
            while (true) {
                // poll RELEASE any unacknowledged records from the previous poll
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        LOG.info("Got {}", record.value().length() > 5
                                ? record.value().substring(0, 6) + "..." : record.value());
                        consumer.acknowledge(record, AcknowledgeType.ACCEPT);
                    } catch (Exception e) {
                        // maybe send to a DLQ for more investigation
                        consumer.acknowledge(record, AcknowledgeType.REJECT);
                    }
                }
                // push the acknowledgments to the brokers
                consumer.commitSync();
            }
        } catch (Throwable e) {
            LOG.error("Unhandled exception", e);
        }
    }

    private static KafkaShareConsumer<String, String> createKafkaShareConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "client-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "my-share-group");
        props.put(ConsumerConfig.SHARE_ACKNOWLEDGEMENT_MODE_CONFIG, "explicit");
        return new KafkaShareConsumer<>(props);
    }
}
