import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Store {

    sealed interface RedisValue
        permits RedisString, RedisList {}

    record RedisString(String value) implements RedisValue {}

    record RedisList(List<String> values) implements RedisValue {}

    record Entry (RedisValue value, Instant expiresAt){};

    // stores client key-value pairs (couples expiration)
    static final Map<String, Entry> data = new ConcurrentHashMap<>();
}
