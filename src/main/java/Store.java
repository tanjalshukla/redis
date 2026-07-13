import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Store {
    static final Map<String, String> data = new ConcurrentHashMap<>();
}
