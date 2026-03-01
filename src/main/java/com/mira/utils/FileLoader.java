package com.mira.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.mira.Main;

public class FileLoader {

    public static String readFileFromPath(String path) throws IOException {
        Path constructedPath = Path.of(path);
        return Files.readString(constructedPath, StandardCharsets.UTF_8);
    }

    public static String readFileFromClassPath(String path) throws IOException {
        try (InputStream is = Main.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("File was not found!");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
