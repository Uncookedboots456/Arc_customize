package dev.arc.assets;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class ArcDarkFileOps {
    private ArcDarkFileOps() {
    }

    static String readUtf8(File file) throws Exception {
        return readUtf8(new FileInputStream(file));
    }

    static String readUtf8(InputStream input) throws Exception {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static void writeUtf8(File file, String content) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Unable to create " + parent);
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    static void copy(InputStream input, File destination) throws Exception {
        File parent = destination.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Unable to create " + parent);
        }

        try (InputStream in = input; FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    static File resolveInside(File root, String path) throws Exception {
        File canonicalRoot = root.getCanonicalFile();
        File file = new File(canonicalRoot, path).getCanonicalFile();
        String rootPath = canonicalRoot.getPath();
        String filePath = file.getPath();
        if (!filePath.equals(rootPath) && !filePath.startsWith(rootPath + File.separator)) {
            throw new IllegalStateException("Path escapes root: " + path);
        }
        return file;
    }

    static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return hex(digest.digest());
    }

    static String hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    static void deleteRecursively(File file) {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }

    static void replaceDirectory(File tmpDir, File finalDir, File backupDir) {
        if (finalDir.exists() && !finalDir.renameTo(backupDir)) {
            throw new IllegalStateException("Unable to move old directory to backup: " + finalDir);
        }
        if (!tmpDir.renameTo(finalDir)) {
            if (backupDir.exists() && !backupDir.renameTo(finalDir)) {
                backupDir.deleteOnExit();
            }
            throw new IllegalStateException("Unable to install directory: " + finalDir);
        }
        deleteRecursively(backupDir);
    }
}
