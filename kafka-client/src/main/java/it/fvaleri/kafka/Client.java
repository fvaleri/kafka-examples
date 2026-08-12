package it.fvaleri.kafka;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.errors.RetriableException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnsupportedVersionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public sealed abstract class Client extends Thread permits Producer, Consumer {
    private static final Logger LOG = LoggerFactory.getLogger(Client.class);
    private static final Random RND = new Random(0);

    protected final Configuration config = Configuration.get();
    protected AtomicLong messageCount = new AtomicLong(0);
    protected AtomicBoolean closed = new AtomicBoolean(false);

    public Client(String threadName) {
        super(threadName);
    }

    @Override
    public void run() {
        try {
            LOG.info("Starting up");
            execute();
            shutdown(null);
        } catch (Throwable e) {
            LOG.error("Unhandled exception", e);
            shutdown(e);
        }
    }

    public void shutdown(Throwable e) {
        if (!closed.get()) {
            LOG.info("Shutting down");
            closed.set(true);
            onShutdown();
            if (e != null) {
                e.printStackTrace();
                System.exit(1);
            } else {
                System.exit(0);
            }
        }
    }

    // implement the execution loop
    abstract void execute();

    // override when custom shutdown logic is needed
    void onShutdown() {
    }
    
    void sleepMs(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    boolean retriable(Exception e) {
        if (e instanceof IllegalArgumentException
            || e instanceof UnsupportedOperationException
            || e instanceof UnsupportedVersionException) {
            return false;
        }
        return e instanceof RetriableException;
    }

    byte[] randomBytes(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Record size must be greater than zero");
        }
        return new String(RND.ints(size, 'A', 'Z' + 1).toArray(), 0, size).getBytes();
    }

    void createTopics(String... topicNames) {
        // Use default RF to avoid NOT_ENOUGH_REPLICAS error with minISR>1
        createTopics(config.bootstrapServers(), -1, -1, topicNames);
    }

    void createTopics(String bootstrapServers, int numPartitions, int replicationFactor, String... topicNames) {
        var props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.CLIENT_ID_CONFIG, "client" + UUID.randomUUID());
        addConfig(props, config.adminConfig());
        addSecurityConfig(props);
        try (var admin = Admin.create(props)) {
            var newTopics = Arrays.stream(topicNames)
                .map(name -> new NewTopic(name, numPartitions, (short) replicationFactor))
                .toList();
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

    void addConfig(Properties props, String config) {
        var addProps = new Properties();
        if (config != null)   {
            try {
                props.load(new StringReader(config.replace(",", "\n")));
            } catch (IOException | IllegalArgumentException e)   {
                throw new IllegalArgumentException("Failed to parse configuration");
            }
        }
        props.putAll(addProps);
    }
    
    void addSecurityConfig(Properties props) {
        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, config.securityProtocol());
        props.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG,
            config.sslHostnameVerification() ? "HTTPS" : "");
        if (config.sslTruststoreType() != null) {
            props.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, config.sslTruststoreType());
            switch (config.sslTruststoreType()) {
                case "PEM" -> props.put(SslConfigs.SSL_TRUSTSTORE_CERTIFICATES_CONFIG, config.sslTruststoreCertificates());
                case "PKCS12", "JKS" -> {
                    props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, config.sslTruststoreLocation());
                    props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, config.sslTruststorePassword());
                }
                default -> throw new IllegalArgumentException("Unsupported truststore type");
            }
        }
        if (config.sslKeystoreType() != null) {
            props.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, config.sslKeystoreType());
            switch (config.sslKeystoreType()) {
                case "PEM" -> {
                    props.put(SslConfigs.SSL_KEYSTORE_CERTIFICATE_CHAIN_CONFIG, config.sslKeystoreCertificateChain());
                    props.put(SslConfigs.SSL_KEYSTORE_KEY_CONFIG, config.sslKeystoreKey());
                }
                case "PKCS12", "JKS" -> {
                    props.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, config.sslKeystoreLocation());
                    props.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, config.sslKeystorePassword());
                }
                default -> throw new IllegalArgumentException("Unsupported keystore type");
            }
        }
        if (config.saslMechanism() != null) {
            props.put(SaslConfigs.SASL_MECHANISM, config.saslMechanism());
            switch (config.saslMechanism()) {
                case "PLAIN" -> props.put(SaslConfigs.SASL_JAAS_CONFIG, getSaslPlainJaasConfig());
                case "SCRAM-SHA-512" -> props.put(SaslConfigs.SASL_JAAS_CONFIG, getSaslScramJaasConfig());
                case "OAUTHBEARER" -> {
                    props.put(SaslConfigs.SASL_JAAS_CONFIG, getSaslOauthJaasConfig());
                    props.put(SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS,
                        "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");
                }
                default -> throw new IllegalArgumentException("Unsupported SASL mechanism");
            }
        }
    }

    String getSaslPlainJaasConfig() {
        return """
            org.apache.kafka.common.security.plain.PlainLoginModule required \
            username="%s" password="%s";\
            """.formatted(config.saslUsername(), config.saslPassword()).strip();
    }

    String getSaslScramJaasConfig() {
        return """
            org.apache.kafka.common.security.scram.ScramLoginModule required \
            username="%s" password="%s";\
            """.formatted(config.saslUsername(), config.saslPassword()).strip();
    }

    String getSaslOauthJaasConfig() {
        return """
            org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required \
            oauth.client.id="%s" oauth.client.secret="%s" oauth.token.endpoint.uri="%s" \
            oauth.ssl.truststore.location="%s" oauth.ssl.truststore.password="%s" oauth.ssl.truststore.type="%s";\
            """.formatted(
                config.saslOauthClientId(),
                config.saslOauthClientSecret(),
                config.saslOauthTokenEndpointUri(),
                config.sslTruststoreLocation(),
                config.sslTruststorePassword(),
                config.sslTruststoreType()
            ).strip();
    }
}
