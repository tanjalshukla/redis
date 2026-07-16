import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Protocol {

    private static final int ASTERISK_BYTE = '*';
    private static final int BULK_STRING = '$';
    private static final int COMPLETE = -1;
    private static final byte CR = '\r';
    private static final byte LF = '\n';

    // for null keys
    private static final String NULL_BULK_STRING = "$-1\r\n";

    public enum Commands {
        ECHO,
        PING,
        SET,
        GET,
        EX,
        PX,
        RPUSH,
        LPUSH,
        LRANGE,
        LLEN
    }

    public enum ExpirationUnit {
        EX,
        PX
    }

    static void handleCommand(BufferedInputStream in, BufferedOutputStream out) throws IOException, NumberFormatException {
        List<String> args = parseArray(in);
        System.out.println("Arguments: " + args.toString());
        Commands command = Commands.valueOf(args.getFirst().toUpperCase());
        switch (command) {
            case ECHO -> {
                if (args.size() != 2) throw new RuntimeException("Invalid number of arguments for ECHO");
                String message = args.get(1);
                writeBulkString(out, message);
            }
            case PING -> {
                writeString(out, "PONG");
            }
            case SET -> {
                Instant expiry = null;
                if (args.size() == 5) {
                    ExpirationUnit expiryType = ExpirationUnit.valueOf(args.get(3).toUpperCase());
                    if (expiryType != ExpirationUnit.EX && expiryType != ExpirationUnit.PX) {
                        throw new RuntimeException("Invalid expiry type: " + args.get(3));
                    }
                    if (ExpirationUnit.EX == expiryType) {
                        // seconds
                        expiry = Instant.now().plusSeconds(Long.parseLong(args.get(4)));
                    } else {
                        // milliseconds
                        expiry = Instant.now().plusMillis(Long.parseLong(args.get(4)));
                    }
                } else if (args.size() != 3) {
                    throw new RuntimeException("Invalid number of arguments for SET");
                }
                Store.data.put(args.get(1), new Store.Entry(
                        new Store.RedisString(args.get(2)), expiry));
                writeString(out, "OK");
            }
            case GET -> {
                String key = args.get(1);
                if (!Store.data.containsKey(key)) {
                    writeNullBulkString(out);
                    break;
                }
                Store.Entry entry = Store.data.get(key);
                if (expired(entry)) {
                    Store.data.remove(key);
                    writeNullBulkString(out);
                } else {
                    writeBulkString(out, ((Store.RedisString) entry.value()).value()); // gets underlying string
                }
            }
            case RPUSH -> {
               pushToRedisList(args, out, Commands.RPUSH);
            }
            case LPUSH -> {
                pushToRedisList(args, out, Commands.LPUSH);
            }
            case LRANGE -> {
                String key = args.get(1);
                Store.Entry entry = Store.data.get(key);
                Store.RedisList redisList;
                // null bulk string if no list or value of key not list or expired
                if (entry == null || !(entry.value() instanceof Store.RedisList list)
                        || expired(entry)) {
                    writeArray(out, Collections.emptyList());
                    return;
                }

                int start = remapNegativeIdx(Integer.parseInt(args.get(2)), list.size());
                int end = remapNegativeIdx(Integer.parseInt(args.get(3)), list.size());
                System.out.println("LRANGE start: " + start + ", end: " + end + ", list size: " + list.size());

                // if start greater than list size or start later than end, empty array
                if (start >= list.size() || start > end) {
                    writeArray(out, Collections.emptyList());
                    return;
                }

                writeArray(out, list.range(start, end));
            }
            case LLEN -> {
                String key = args.get(1);
                Store.Entry entry = Store.data.get(key);
                if (entry == null || !(entry.value() instanceof Store.RedisList list)) {
                    writeInteger(out, 0);
                } else {
                    writeInteger(out, list.size());
                }
            }
            default -> throw new RuntimeException("Unknown command: " + args.get(0));
        }
    }

    // intended for LPUSH and RPUSH, inserts new list if empty/expired key
    static void pushToRedisList(List<String> args, BufferedOutputStream out, Commands command) throws IOException {
        String key = args.get(1);

        Store.Entry entry = Store.data.get(key);
        Store.RedisList redisList;
        if (entry == null) {
            // inserting new redis list
            redisList = new Store.RedisList(new ArrayList<>());
            Store.data.put(key, new Store.Entry(redisList, null));
        } else if (entry.value() instanceof Store.RedisList list) {
            if (expired(entry)) {
                // replace expired key-value pair
                redisList = new Store.RedisList(new ArrayList<>());
                Store.data.put(key, new Store.Entry(redisList, null));
            } else {
                redisList = list;
            }
        } else {
            // value of entry not redis list
            writeNullBulkString(out);
            return;
        }

        if (command == Commands.LPUSH) {
            redisList.queueAll(args.subList(2, args.size()));
            writeInteger(out, redisList.size());
            return;
        } else  if (command == Commands.RPUSH) {
            redisList.pushAll(args.subList(2, args.size()));
            writeInteger(out, redisList.size());
            return;
        }

        throw new IllegalArgumentException("Command must be LPUSH or RPUSH, provided: " + command);
    }

    /**
     * RESP format helpers
     */
    static void writeString(BufferedOutputStream out, String string) throws IOException {
        out.write(("+" + string + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    static void writeInteger(BufferedOutputStream out, long integer) throws IOException {
        out.write((":" + integer + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    static void writeBulkString(BufferedOutputStream out, String string) throws IOException {
        out.write(("$" + string.length() + "\r\n" + string + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    static void writeNullBulkString(BufferedOutputStream out) throws IOException {
        out.write(NULL_BULK_STRING.getBytes(StandardCharsets.UTF_8));
    }

    static void writeArray(BufferedOutputStream out, List<String> values) throws IOException {
        // write num elements
        out.write(("*" + values.size() + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (String value : values) {
            writeBulkString(out, value);
        }
    }

    static List<String> parseArray(BufferedInputStream in) throws IOException {
        int type;
        switch (type = in.read()) {
            case ASTERISK_BYTE -> { // resp array
                int count = parseIntLine(in);
                List<String> args = new ArrayList<>(count);

                for (int i = 0; i < count; i++) {
                    // read bulk string
                    int bulkType = in.read();
                    if (bulkType != BULK_STRING) throw new RuntimeException("Expected bulk string");

                    int bulkLength = parseIntLine(in);
                    byte[] bulkData = in.readNBytes(bulkLength);
                    args.add(new String(bulkData, StandardCharsets.UTF_8));
                    consumeCRLF(in);
                }
                return args;
            }
            case COMPLETE -> {
                // end of stream
                throw new EOFException("Client completed");
            }
            default -> {
                // expect array
                throw new RuntimeException("Unknown command");
            }
        }
    }

    // read bytes into num until \r, consume \n
    private static int parseIntLine(BufferedInputStream in) throws IOException {
        int res = 0;
        // handle leading minus sign
        int b;
        boolean negative = false;
        while ((b = in.read()) != -1) {
            if (b == '-') {
                negative = true;
                continue;
            }
            if (b == CR) {
                if (in.read() != LF) {
                    throw new RuntimeException("Expected newline");
                }
                break;
            }

            res = res * 10 + (b - '0'); // convert from ASCII to int
        }
        return negative ? -res : res;
    }

    private static void consumeCRLF(BufferedInputStream in) throws IOException {
        if (in.read() != CR) {
            throw new RuntimeException("Expected carriage return");
        }
        if (in.read() != LF) {
            throw new RuntimeException("Expected newline");
        }
    }

    static int remapNegativeIdx(int i, int size) {
        if (i < 0) {
            i = size + i;
            return Math.max(i, 0);
        }
        return i;
    }

    static boolean expired(Store.Entry entry) {
        return entry.expiresAt() != null && entry.expiresAt().isBefore(Instant.now());
    }

}
