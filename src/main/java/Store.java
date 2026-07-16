import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
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

        void queueAll(List<String> values) {
            for  (String value : values) {
                this.values.addFirst(value);
            }
        }

        void pushAll(List<String> values) {
            this.values.addAll(values);
        }

        String pop() {
            return this.values.removeFirst();
        }

        List<String> range(int start, int end) {
            return values.subList(start, Math.min(end, values.size() - 1) + 1); // inclusive of ending index
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
