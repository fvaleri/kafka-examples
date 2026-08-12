package it.fvaleri.kafka;

import com.google.gson.Gson;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class Data {
    private static final Logger LOG = LoggerFactory.getLogger(Data.class);
    public static KafkaProducer<Integer, String> producerInstance = null;

    public static void main(String[] args) throws Exception {
        LOG.info("Generating fake data");
        var records = new ArrayList<ProducerRecord<Integer, String>>();
        var gson = new Gson();

        try (var producer = new KafkaProducer<Integer, String>(createConfig())) {
            producerInstance = producer;

            // Two users
            var user0 = new UserProfile(0, "Federico", "00148", new String[]{"coding", "running"});
            var user1 = new UserProfile(1, "Anna", "00158", new String[]{"hiking", "dancing"});
            records.add(new ProducerRecord<>(Main.USER_PROFILE_TOPIC, user0.userId(), gson.toJson(user0)));
            records.add(new ProducerRecord<>(Main.USER_PROFILE_TOPIC, user1.userId(), gson.toJson(user1)));

            // Profile update
            String[] newInterests = {"hiking", "stream processing"};
            records.add(new ProducerRecord<>(Main.USER_PROFILE_TOPIC, user1.userId(), gson.toJson(user1.update("00149", newInterests))));

            // Two searches
            var search0 = new Search(0, "running shorts");
            var search1 = new Search(1, "light jacket");
            records.add(new ProducerRecord<>(Main.SEARCH_TOPIC, search0.userId(), gson.toJson(search0)));
            records.add(new ProducerRecord<>(Main.SEARCH_TOPIC, search1.userId(), gson.toJson(search1)));

            // Three clicks
            var view0 = new PageView(0, "running-mens/shorts-5-inches");
            var view1 = new PageView(1, "product/dirt-craft-bike-mountain-biking-jacket");
            var view2 = new PageView(1, "product/ultralight-down-jacket");
            records.add(new ProducerRecord<>(Main.PAGE_VIEW_TOPIC, view0.userId(), gson.toJson(view0)));
            records.add(new ProducerRecord<>(Main.PAGE_VIEW_TOPIC, view1.userId(), gson.toJson(view1)));
            records.add(new ProducerRecord<>(Main.PAGE_VIEW_TOPIC, view2.userId(), gson.toJson(view2)));

            for (var record : records) {
                LOG.info("Sending record with key {} to topic {}", record.key(), record.topic());
                producer.send(record, (RecordMetadata r, Exception e) -> {
                    if (e != null) {
                        LOG.error("Error producing to topic {}: {}", r.topic(), e.getMessage(), e);
                    }
                });
            }

            // Sleep 5 seconds to make sure we recognize the new events as a separate session
            records.clear();
            TimeUnit.MILLISECONDS.sleep(5_000);

            // One more search
            var search2 = new Search(1, "hiking boots");
            records.add(new ProducerRecord<>(Main.SEARCH_TOPIC, search2.userId(), gson.toJson(search2)));

            // Two clicks
            var view3 = new PageView(1, "product/salomon-x-ultra-3-mid-gtx");
            var view4 = new PageView(1, "product/merrell-moab-2-mid-wp");
            records.add(new ProducerRecord<>(Main.PAGE_VIEW_TOPIC, view3.userId(), gson.toJson(view3)));
            records.add(new ProducerRecord<>(Main.PAGE_VIEW_TOPIC, view4.userId(), gson.toJson(view4)));

            // We want to make sure we have results for an unknown users too
            var view5 = new PageView(-1, "product/osprey-atmos-65-ag-pack");
            records.add(new ProducerRecord<>(Main.PAGE_VIEW_TOPIC, view5.userId(), gson.toJson(view5)));

            for (var record : records) {
                LOG.info("Sending record with key {} to topic {}", record.key(), record.topic());
                producer.send(record, (RecordMetadata r, Exception e) -> {
                    if (e != null) {
                        LOG.error("Error producing to topic {}: {}", r.topic(), e.getMessage(), e);
                    }
                });
            }
        }
        LOG.info("DONE");
    }

    static Properties createConfig() {
        var config = new Properties();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Main.BOOTSTRAP_SERVERS);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, IntegerSerializer.class.getName());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return config;
    }

    public record UserProfile(int userId, String userName, String zipcode, String[] interests) {
        public UserProfile update(String zipcode, String[] interests) {
            return new UserProfile(userId, userName, zipcode, interests);
        }
    }

    public record UserActivity(int userId, String userName, String zipcode, String[] interests, String searchTerm, String page) {
        public UserActivity updateSearch(String searchTerm) {
            return new UserActivity(userId, userName, zipcode, interests, searchTerm, page);
        }
    }

    public record Search(int userId, String searchTerms) { }

    public record PageView(int userId, String page) { }
}
