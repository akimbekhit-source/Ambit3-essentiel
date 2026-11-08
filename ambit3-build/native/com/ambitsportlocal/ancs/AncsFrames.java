package com.ambitsportlocal.ancs;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

/** ANCS wire format observed in Movescount 1.5.11. Transport independent. */
public final class AncsFrames {
    public static final UUID SERVICE = UUID.fromString("7905f431-b5ce-4e99-a40f-4b1e122d00d0");
    public static final UUID NOTIFICATIONS = UUID.fromString("9fbf120d-6301-42d9-8c58-25e699a21dbd");
    public static final UUID CONTROL = UUID.fromString("69d1d8f3-45e1-49a8-9821-9bbdfdaad9d9");
    public static final UUID DATA = UUID.fromString("22eac6e9-24d6-4bb5-be44-b36ace7c7bfb");

    private AncsFrames() {}

    public static byte[] event(int uid, boolean removed, int category) {
        if (category < 0 || category > 11) throw new IllegalArgumentException("Invalid category");
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .put((byte) (removed ? 2 : 0)).put((byte) 2)
            .put((byte) category).put((byte) 1).putInt(uid).array();
    }

    /** Reply to Get Notification Attributes for a known UID. */
    public static byte[] attributes(byte[] request, int uid, String app, String title, String message) {
        ByteBuffer input = ByteBuffer.wrap(request).order(ByteOrder.LITTLE_ENDIAN);
        if (input.remaining() < 6 || input.get() != 0 || input.getInt() != uid) {
            throw new IllegalArgumentException("Invalid request or unknown notification");
        }
        ByteArrayOutputStream reply = new ByteArrayOutputStream();
        reply.write(request, 0, 5);
        while (input.hasRemaining()) {
            int id = Byte.toUnsignedInt(input.get());
            int max = 512;
            if (id >= 1 && id <= 3) {
                if (input.remaining() < 2) throw new IllegalArgumentException("Missing maximum length");
                max = Math.min(max, Short.toUnsignedInt(input.getShort()));
            }
            String value;
            switch (id) {
                case 0: value = app; break;
                case 1: value = title; break;
                case 3: value = message; break;
                case 4: value = Integer.toString(message.getBytes(StandardCharsets.UTF_8).length); break;
                default: value = "";
            }
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            int length = Math.min(max, bytes.length);
            while (length < bytes.length && length > 0 && (bytes[length] & 0xc0) == 0x80) length--;
            reply.write(id);
            reply.write(length & 255);
            reply.write(length >>> 8);
            byte[] field = Arrays.copyOf(bytes, length);
            reply.write(field, 0, field.length);
        }
        return reply.toByteArray();
    }
}
