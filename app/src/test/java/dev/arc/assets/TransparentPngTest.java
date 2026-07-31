package dev.arc.assets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.InflaterInputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class TransparentPngTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void writesValidTransparentRgbaPng() throws Exception {
        File output = temporaryFolder.newFile("transparent.png");
        TransparentPng.write(output, 3, 2);
        byte[] png = Files.readAllBytes(output.toPath());

        assertArrayEquals(
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a},
                java.util.Arrays.copyOf(png, 8)
        );

        DataInputStream input = new DataInputStream(new ByteArrayInputStream(png, 8, png.length - 8));
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        boolean sawHeader = false;
        while (input.available() > 0) {
            int length = input.readInt();
            byte[] typeBytes = new byte[4];
            input.readFully(typeBytes);
            String type = new String(typeBytes, StandardCharsets.US_ASCII);
            byte[] data = new byte[length];
            input.readFully(data);
            input.readInt();
            if ("IHDR".equals(type)) {
                DataInputStream header = new DataInputStream(new ByteArrayInputStream(data));
                assertEquals(3, header.readInt());
                assertEquals(2, header.readInt());
                assertEquals(8, header.readUnsignedByte());
                assertEquals(6, header.readUnsignedByte());
                sawHeader = true;
            } else if ("IDAT".equals(type)) {
                compressed.write(data);
            } else if ("IEND".equals(type)) {
                break;
            }
        }
        assertTrue(sawHeader);

        ByteArrayOutputStream pixels = new ByteArrayOutputStream();
        try (InflaterInputStream inflater = new InflaterInputStream(
                new ByteArrayInputStream(compressed.toByteArray())
        )) {
            byte[] buffer = new byte[128];
            int read;
            while ((read = inflater.read(buffer)) != -1) {
                pixels.write(buffer, 0, read);
            }
        }
        byte[] raw = pixels.toByteArray();
        assertEquals((1 + 3 * 4) * 2, raw.length);
        assertArrayEquals(new byte[raw.length], raw);
    }
}
