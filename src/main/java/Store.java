import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Store {

    sealed interface RedisValue
        permits RedisString, RedisList {}

    static final class RedisList implements RedisValue {
        private final List<String> values;

        public RedisList(List<String> values) {
            this.values = values;
        }

        void push(String value) {
            values.add(value);
        }

        void pushAll(List<String> values) {
            this.values.addAll(values);
        }

        int size() {
            return values.size();
        }

        List<String> values() {
            return Collections.unmodifiableList(values);
        }

    }

    record RedisString(String value) implements RedisValue {}



    record Entry (RedisValue value, Instant expiresAt){};

    // stores client key-value pairs (couples expiration)
    static final Map<String, Entry> data = new ConcurrentHashMap<>();
}
