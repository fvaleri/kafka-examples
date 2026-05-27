package it.fvaleri.kafka;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record Configuration(
    String clientType,
    int messageSizeBytes,
    long numMessages,
    long processingDelayMs,
    long pollTimeoutMs,
    String bootstrapServers,
    String clientId,
    String securityProtocol,
    String topicName,
    String groupId,
    String adminConfig,
    String producerConfig,
    String consumerConfig,
    boolean sslHostnameVerification,
    String sslTruststoreType,
    String sslTruststoreCertificates,
    String sslTruststoreLocation,
    String sslTruststorePassword,
    String sslKeystoreType,
    String sslKeystoreCertificateChain,
    String sslKeystoreKey,
    String sslKeystoreLocation,
    String sslKeystorePassword,
    String saslMechanism,
    String saslUsername,
    String saslPassword,
    String saslOauthTokenEndpointUri,
    String saslOauthClientId,
    String saslOauthClientSecret
) {
    private static final Logger LOG = LoggerFactory.getLogger(Configuration.class);

    private static final Properties PROPS = loadPropertiesFromFile();
    private static final Map<String, String> CONFIG = new TreeMap<>();
    private static final Configuration INSTANCE = load();

    static {
        LOG.info("=======================================================");
        CONFIG.forEach((k, v) -> LOG.info("{}: {}", k,
            (contains(k, "password", "keystore.key") && v != null) ? "*****" : v));
        LOG.info("=======================================================");
    }

    public static Configuration get() {
        return INSTANCE;
    }

    private static Configuration load() {
        return new Configuration(
            getOrDefault("client.type", "producer"),
            getOrDefault("message.size.bytes", 100, Integer::parseInt),
            getOrDefault("num.messages", Long.MAX_VALUE, Long::parseLong),
            getOrDefault("processing.delay.ms", 0L, Long::parseLong),
            getOrDefault("poll.timeout.ms", 1_000L, Long::parseLong),
            getOrDefault("bootstrap.servers", null),
            getOrDefault("client.id", "client-" + UUID.randomUUID()),
            getOrDefault("security.protocol", "PLAINTEXT"),
            getOrDefault("topic.name", null),
            getOrDefault("group.id", "my-group"),
            getOrDefault("admin.config", null),
            getOrDefault("producer.config", null),
            getOrDefault("consumer.config", null),
            getOrDefault("ssl.hostname.verification", true, Boolean::parseBoolean),
            getOrDefault("ssl.truststore.type", null),
            getOrDefault("ssl.truststore.certificates", null),
            getOrDefault("ssl.truststore.location", null),
            getOrDefault("ssl.truststore.password", null),
            getOrDefault("ssl.keystore.type", null),
            getOrDefault("ssl.keystore.certificate.chain", null),
            getOrDefault("ssl.keystore.key", null),
            getOrDefault("ssl.keystore.location", null),
            getOrDefault("ssl.keystore.password", null),
            getOrDefault("sasl.mechanism", null),
            getOrDefault("sasl.username", null),
            getOrDefault("sasl.password", null),
            getOrDefault("sasl.oauth.token.endpoint.uri", null),
            getOrDefault("sasl.oauth.client.id", null),
            getOrDefault("sasl.oauth.client.secret", null)
        );
    }

    private static Properties loadPropertiesFromFile() {
        var prop = new Properties();
        try {
            prop.load(Configuration.class.getClassLoader()
                    .getResourceAsStream("application.properties"));
            return prop;
        } catch (IOException e) {
            throw new RuntimeException("Load configuration error", e);
        }
    }

    private static String getOrDefault(String key, String defaultValue) {
        return getOrDefault(key, defaultValue, String::toString);
    }

    private static <T> T getOrDefault(String key, T defaultValue, Function<String, T> converter) {
        String envKey = key.toUpperCase(Locale.ENGLISH).replaceAll("\\.", "_");
        T result = Optional.ofNullable(System.getenv(envKey))
            .or(() -> Optional.ofNullable(PROPS.getProperty(key)))
            .map(converter)
            .orElse(defaultValue);
        CONFIG.put(key, String.valueOf(result));
        return result;
    }

    private static boolean contains(String key, String... words) {
        return Arrays.stream(words).anyMatch(key::contains);
    }
}
