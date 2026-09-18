package com.mira.lib.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class FastPrinter {

    public static final FastPrinter INSTANCE = new FastPrinter();

    private final Object lock = new Object();
    private OutputStream out;
    private byte[] scratch = new byte[64];

    private FastPrinter() {
    }

    public void setOut(OutputStream out) {
        synchronized (lock) {
            this.out = out;
        }
    }

    public void print(Object value) {
        write(value, false);
    }

    public void println(Object value) {
        write(value, true);
    }

    private void write(Object value, boolean newline) {
        synchronized (lock) {
            if (out == null) {
                if (newline) {
                    System.out.println(value);
                } else {
                    System.out.print(value);
                }
                return;
            }
            try {
                if (value instanceof Long l) {
                    writeLong(l, newline);
                } else {
                    writeString(String.valueOf(value), newline);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private void writeLong(long v, boolean newline) throws IOException {
        if (v == Long.MIN_VALUE) {
            writeString(Long.toString(v), newline);
            return;
        }
        byte[] buf = scratch;
        int end = buf.length - 1;
        int pos = end;
        long a = Math.abs(v);
        do {
            buf[--pos] = (byte) ('0' + (int) (a % 10));
            a /= 10;
        } while (a > 0);
        if (v < 0) {
            buf[--pos] = '-';
        }
        int len = end - pos;
        if (newline) {
            buf[end] = '\n';
            len++;
        }
        out.write(buf, pos, len);
    }

    private void writeString(String s, boolean newline) throws IOException {
        int n = s.length();
        boolean ascii = true;
        for (int i = 0; i < n; i++) {
            if (s.charAt(i) > 127) {
                ascii = false;
                break;
            }
        }
        if (!ascii) {
            out.write(s.getBytes(StandardCharsets.UTF_8));
            if (newline) {
                out.write('\n');
            }
            return;
        }
        byte[] buf = n + 1 <= scratch.length ? scratch : new byte[n + 1];
        for (int i = 0; i < n; i++) {
            buf[i] = (byte) s.charAt(i);
        }
        int len = n;
        if (newline) {
            buf[n] = '\n';
            len = n + 1;
        }
        out.write(buf, 0, len);
    }
}
