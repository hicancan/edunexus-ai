package com.edunexus.api.common;

public final class FilenameUtil {
    private FilenameUtil() {
    }

    public static String sanitize(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-\\u4e00-\\u9fa5]", "_");
    }
}
