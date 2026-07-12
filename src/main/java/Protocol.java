import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class Protocol {

    private static final int ASTERISK_BYTE = '*';
    private static final int BULK_STRING = '$';

    public enum commands {
        ECHO
    }

    static void handleCommand(BufferedInputStream in, BufferedOutputStream out) throws IOException {
        List<String> args = processArgs(in);

        switch (args.get(0).toUpperCase()) {
            case "ECHO" -> {
                if (args.size() != 2) throw new RuntimeException("Invalid number of arguments for ECHO");
                String message = args.get(1);
                String response = "+" + message + "\r\n";
                writeString(new BufferedOutputStream(out), message);
            }
            default -> throw new RuntimeException("Unknown command: " + args.get(0));
        }
    }

    static void writeString(BufferedOutputStream out, String string) throws IOException {
        out.write(("+" + string + "\r\n").getBytes());
    }

    static List<String> processArgs(BufferedInputStream in) throws IOException {
        int type;
        switch (type = in.read()) {
            case ASTERISK_BYTE -> { // resp array
                int count = in.read();
                List<String> args = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    // read bulk string
                    int bulkType = in.read();
                    if (bulkType != BULK_STRING) throw new RuntimeException("Expected bulk string");

                    int bulkLength = parseIntLine(in);
                    byte[] bulkData = in.readNBytes(bulkLength);
                    args.add(new String(bulkData));
                    consumeCRLF(in);
                }
                return args;
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
            if (b == '\r') {
                if (in.read() != '\n') {
                    throw new RuntimeException("Expected newline");
                }
                break;
            }

            res = res * 10 + (b - '0'); // convert from ASCII to int
        }
        return negative ? -res : res;
    }

    private static void consumeCRLF(BufferedInputStream in) throws IOException {
        if (in.read() != '\r') {
            throw new RuntimeException("Expected carriage return");
        }
        if (in.read() != '\n') {
            throw new RuntimeException("Expected newline");
        }
    }

}
