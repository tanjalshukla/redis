import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Protocol {

    private static final int ASTERISK_BYTE = '*';
    private static final int BULK_STRING = '$';
    private static final int COMPLETE = -1;
    private static final byte CR = '\r';
    private static final byte LF = '\n';

    public enum Commands {
        ECHO,
        PING
    }

    static void handleCommand(BufferedInputStream in, BufferedOutputStream out) throws IOException {
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
            default -> throw new RuntimeException("Unknown command: " + args.get(0));
        }
    }

    static void writeString(BufferedOutputStream out, String string) throws IOException {
        out.write(("+" + string + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    static void writeBulkString(BufferedOutputStream out, String string) throws IOException {
        out.write(("$" + string.length() + "\r\n" + string + "\r\n").getBytes(StandardCharsets.UTF_8));
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

}
