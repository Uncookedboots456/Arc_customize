package dev.arc.assets;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

final class TransparentPng {
    private static final byte[] SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    private TransparentPng() {
    }

    static void write(File output, int width, int height) throws Exception {
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192) {
            throw new IllegalArgumentException("Invalid PNG dimensions: " + width + "x" + height);
        }

        try (OutputStream file = new FileOutputStream(output);
             DataOutputStream png = new DataOutputStream(file)) {
            png.write(SIGNATURE);

            ByteArrayOutputStream headerBytes = new ByteArrayOutputStream(13);
            try (DataOutputStream header = new DataOutputStream(headerBytes)) {
                header.writeInt(width);
                header.writeInt(height);
                header.writeByte(8);
                header.writeByte(6);
                header.writeByte(0);
                header.writeByte(0);
                header.writeByte(0);
            }
            writeChunk(png, "IHDR", headerBytes.toByteArray());

            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
            try (DeflaterOutputStream pixels = new DeflaterOutputStream(compressed, deflater)) {
                byte[] transparentRow = new byte[1 + width * 4];
                for (int row = 0; row < height; row++) {
                    pixels.write(transparentRow);
                }
            }
            writeChunk(png, "IDAT", compressed.toByteArray());
            writeChunk(png, "IEND", new byte[0]);
        }
    }

    private static void writeChunk(DataOutputStream output, String type, byte[] data) throws Exception {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);

        output.writeInt(data.length);
        output.write(typeBytes);
        output.write(data);
        output.writeInt((int) crc.getValue());
    }
}
