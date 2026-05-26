package com.example.astrastudioopenai.common.utils;

import java.util.Set;

public final class FileTypes {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp");

    private FileTypes() {
    }

    public static boolean isImageFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String lowerName = fileName.toLowerCase();
        for (String ext : IMAGE_EXTENSIONS) {
            if (lowerName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    public static String detectContentType(String fileName) {
        return isImageFile(fileName) ? "image" : "text";
    }
}
