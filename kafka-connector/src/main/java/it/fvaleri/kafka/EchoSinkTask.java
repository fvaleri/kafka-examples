package it.fvaleri.kafka;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

// Each task runs in its own thread
public class EchoSinkTask extends SinkTask {
    private static final Logger LOG = LoggerFactory.getLogger(EchoSinkTask.class);

    private long failTaskAfterRecords, recordCounter;
    private BiFunction<Object, Object, Void> logOnLevel;

    @Override
    public String version() {
        return new EchoSinkConnector().version();
    }

    @Override
    public void start(Map<String, String> props) {
        try {
            String failTaskAfterRecords = props.get(EchoSinkConnector.FAIL_TASK_AFTER_RECORDS_CONFIG);
            this.failTaskAfterRecords = (failTaskAfterRecords != null) ? Long.parseLong(failTaskAfterRecords) : 0L;
        } catch (Throwable e)  {
            LOG.warn("Failed to parse {}. The task will not fail intentionally.", EchoSinkConnector.FAIL_TASK_AFTER_RECORDS_CONFIG, e);
        }

        var logLevel = Level.INFO;
        try {
            logLevel = Level.valueOf(props.get(EchoSinkConnector.LEVEL_CONFIG));
        } catch (Throwable e) {
            LOG.warn("Failed to decode log level {}. Default log level INFO will be used.", props.get(EchoSinkConnector.LEVEL_CONFIG));
        }

        logOnLevel = switch (logLevel) {
            case INFO -> (key, value) -> { LOG.info("Received message with key '{}' and value '{}'", key, value); return null; };
            case ERROR -> (key, value) -> { LOG.error("Received message with key '{}' and value '{}'", key, value); return null; };
            case WARN -> (key, value) -> { LOG.warn("Received message with key '{}' and value '{}'", key, value); return null; };
            case DEBUG -> (key, value) -> { LOG.debug("Received message with key '{}' and value '{}'", key, value); return null; };
            case TRACE -> (key, value) -> { LOG.trace("Received message with key '{}' and value '{}'", key, value); return null; };
        };
    }

    // The main logic is here
    @Override
    public void put(Collection<SinkRecord> sinkRecords) {
        for (var record : sinkRecords) {
            var headers = new HashMap<String, String>();
            for (var header : record.headers()) {
                headers.put(header.key(), header.value().toString());
            }

            logRecord(record.key(), record.value());

            if (failTaskAfterRecords > 0 && ++recordCounter >= failTaskAfterRecords)   {
                LOG.warn("Failing as requested after {} records", failTaskAfterRecords);
                throw new RuntimeException("Intentional task failure after receiving " + failTaskAfterRecords + " records");
            }
        }
    }

    @Override
    public void stop() {
        // Nothing to do
    }

    private void logRecord(Object key, Object value)  {
        logOnLevel.apply(key, value);
    }
}
