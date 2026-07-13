import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Store {
    record Entry (String value, Instant expiresAt){};

    // stores client key-value pairs (couples expiration)
    static final Map<String, Entry> data = new ConcurrentHashMap<>();
}
