package com.mira.runtime.values;

public class BytesValue {

    private final byte[] data;

    public BytesValue(byte[] data) {
        this.data = data.clone();
    }

    public BytesValue(int size) {
        this.data = new byte[size];
    }

    public byte[] getData() {
        return data;
    }

    public int size() {
        return data.length;
    }

    @Override
    public String toString() {
        if (data.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < data.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.format("0x%02x", data[i] & 0xFF));
        }
        sb.append("]");
        return sb.toString();
    }
}
