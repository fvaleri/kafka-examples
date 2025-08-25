package it.fvaleri.kafka;

import org.HdrHistogram.Histogram;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final int NUMBER_OF_MESSAGES = 50_000;
    private static final int MESSAGE_SENDING_INTERVAL_MILLIS = 1;
    private static final Histogram HISTOGRAM = new Histogram(5);

    public static void main(String[] args) {
        try {
            final AtomicBoolean shutdown = new AtomicBoolean(false);

            try (final Admin adminClient = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS))) {
                adminClient.deleteTopics(List.of("test"));
                adminClient.createTopics(Collections.singleton(new NewTopic("test", 1, (short) 1))).all().get(100, TimeUnit.SECONDS);
            }

            final Properties producerProps = new Properties();
            producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
            producerProps.put(ProducerConfig.CLIENT_ID_CONFIG, UUID.randomUUID().toString());
            producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, LongSerializer.class.getName());
            // to minimize latency in low throughput use cases set linger.ms=0 on the producer
            producerProps.put(ProducerConfig.LINGER_MS_CONFIG, 0);

            final Properties consumerProps = new Properties();
            consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
            consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, LongDeserializer.class.getName());
            consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
            consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
            // to minimize latency in low throughput use cases set group.coordinator.append.linger.ms=0 on the broker

            try (final KafkaProducer<String, Long> kafkaProducer = new KafkaProducer<>(producerProps);
                 final KafkaConsumer<String, Long> kafkaConsumer = new KafkaConsumer<>(consumerProps)) {
                kafkaConsumer.assign(List.of(new TopicPartition("test", 0)));

                final ExecutorService executorService = Executors.newFixedThreadPool(2);
                executorService.execute(() -> {
                    sendMessages(kafkaProducer, shutdown);
                    kafkaConsumer.wakeup();
                });
                executorService.execute(() -> receiveMessages(kafkaConsumer, shutdown));
                stopExecutor(executorService);

                System.out.println("50th percentile: " + HISTOGRAM.getValueAtPercentile(50) / 1_000 + " us");
                System.out.println("90th percentile: " + HISTOGRAM.getValueAtPercentile(90) / 1_000 + " us");
                System.out.println("99th percentile: " + HISTOGRAM.getValueAtPercentile(99) / 1_000 + " us");
                System.out.println("99.9th percentile: " + HISTOGRAM.getValueAtPercentile(99.9) / 1_000 + " us");
            }
        } catch (Throwable e) {
            System.err.println("Unhandled exception");
            e.printStackTrace();
        }
    }

    private static void sendMessages(final KafkaProducer<String, Long> kafkaProducer, final AtomicBoolean shutdown) {
        for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            kafkaProducer.send(new ProducerRecord<>("test", System.nanoTime()));
            if (i % 1000 == 0) {
                System.out.println("Sent: " + i);
            }
            sleep(MESSAGE_SENDING_INTERVAL_MILLIS);
        }
        shutdown.set(true);
    }

    private static void receiveMessages(final KafkaConsumer<String, Long> kafkaConsumer, final AtomicBoolean shutdown) {
        try {
            while (!shutdown.get()) {
                final ConsumerRecords<String, Long> records = kafkaConsumer.poll(Duration.ofSeconds(60));
                records.forEach(record ->
                        HISTOGRAM.recordValue(System.nanoTime() - record.value())
                );
                kafkaConsumer.commitSync();
            }
        } catch (final WakeupException e) {
            // ignored
        }
    }

    private static void sleep(final long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void stopExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    System.err.println("ExecutorService did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
