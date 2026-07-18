import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Store {

    sealed interface RedisValue
        permits RedisString, RedisList, RedisSet, RedisZSet, RedisStream, RedisHash, RedisVectorSet {
        String printType();
    }

    static final class RedisSet implements RedisValue {
        @Override
        public String printType() {
            return "set";
        }
    }

    static final class RedisZSet implements RedisValue {
        @Override
        public String printType() {
            return "zset";
        }
    }

    static final class RedisStream implements RedisValue {
        @Override
        public String printType() {
            return "stream";
        }
    }

    static final class RedisHash implements RedisValue {
        @Override
        public String printType() {
            return "hash";
        }
    }

    static final class RedisVectorSet implements RedisValue {
        @Override
        public String printType() {
            return "vectorset";
        }
    }


    static final class RedisList implements RedisValue {
        private final List<String> values;
        // lock per list in thread-safe hashmap
        private final ReentrantLock lock  = new ReentrantLock();
        private final Condition condition = lock.newCondition();

        public RedisList(List<String> values) {
            this.values = values;
        }

        void queueAll(List<String> values) {
            lock.lock();
            try {
                for  (String value : values) {
                    this.values.addFirst(value);
                }
                condition.signalAll(); // notify sleeping threads
            } finally {
                lock.unlock();
            }
        }

        void pushAll(List<String> values) {
            lock.lock();
            try {
                this.values.addAll(values);
                condition.signalAll(); // notify sleeping threads
            } finally {
                lock.unlock();
            }
        }

        String pop() {
            lock.lock();
            try {
                if (this.values.isEmpty()) return null;
                return this.values.removeFirst();
            } finally {
                lock.unlock();
            }
        }

        String blockingPop(long timeoutMillis) throws InterruptedException {
            lock.lock();
            try {
                long deadlineNanos = timeoutMillis == 0 ? 0 : System.nanoTime() + (timeoutMillis * 1_000_000);
                while (values.isEmpty()) {
                    if (timeoutMillis == 0) {
                        condition.await();
                    } else {
                        long remainingNanos = deadlineNanos - System.nanoTime();
                        if (remainingNanos <= 0) return null; // timed out
                        condition.awaitNanos(remainingNanos);
                    }
                }
                return values.removeFirst();
            } finally {
                lock.unlock();
            }

        }

        List<String> popMany(int count ) {
            lock.lock();
            try {
                List<String> removed = new ArrayList<>();
                int limit = Math.min(count, this.values.size());
                for (int i = 0; i < limit; i++) {
                    removed.add(this.values.removeFirst());
                }
                return removed;
            } finally {
                lock.unlock();
            }
        }

        List<String> range(int start, int end) {
            lock.lock();
            try {
                // inclusive of ending index
                return new ArrayList<>(this.values.subList(start, Math.min(end, this.values.size() - 1) + 1));
            } finally {
                lock.unlock();
            }
        }

        int size() {
            return values.size();
        }

        List<String> values() {
            return Collections.unmodifiableList(values);
        }

        @Override
        public String printType() {
            return "list";
        }

    }

    static final class RedisString implements RedisValue {
        final String value;

        RedisString(String value) {
            this.value = value;
        }

        String value() {
            return this.value;
        }

        @Override
        public String printType() {
            return "string";
        }
    }

    record Entry (RedisValue value, Instant expiresAt){};

    // stores client key-value pairs (couples expiration)
    static final Map<String, Entry> data = new ConcurrentHashMap<>();
}
