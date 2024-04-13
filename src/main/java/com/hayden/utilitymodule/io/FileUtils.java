package com.hayden.utilitymodule.io;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@Slf4j
public class FileUtils {

    public static InputStream getResourceAsStream(String value) {
        return FileUtils.class.getClassLoader().getResourceAsStream(value);
    }

    public static boolean writeBytesToFile(byte[] data, Path file) {
        try {
            if (!file.toFile().exists() && !file.toFile().createNewFile()) {
                log.error("Failed to write to file {}. Could not create file", file.toFile());
                return false;
            }
            try (var fw = new FileOutputStream(file.toFile())) {
                fw.write(data);
            }
            return true;
        } catch (IOException e) {
            log.error("Failed to write to file: {}.", e.getMessage());
            return false;
        }
    }

    public static boolean writeToFile(String data, Path file) {
        try {
            if (!file.toFile().exists() && !file.toFile().createNewFile()) {
                log.error("Failed to write to file {}. Could not create file", file.toFile());
                return false;
            }
            try (FileWriter fw = new FileWriter(file.toFile())) {
                fw.write(data);
            }
            return true;
        } catch (IOException e) {
            log.error("Failed to write to file: {}.", e.getMessage());
            return false;
        }
    }

    public static Stream<Path> GetFilesRecursive(Path path) {
        try  {
            var files = Files.list(path);
            return files.flatMap(p -> {
                if (p.toFile().isFile()) {
                    return Stream.of(p);
                } else if (p.toFile().isDirectory()) {
                    return GetFilesRecursive(p);
                }

                return Stream.empty();
            });
        } catch (IOException ignored) {
            // ignore
        }
        return Stream.empty();
    }

    public static boolean deleteFilesRecursive(Path path) {
        return GetFilesRecursive(path).allMatch(p -> {
            try {
                Files.delete(p);
                return true;
            } catch (IOException e) {
                log.error("Could not delete file {} with error {}.", p.toFile().getAbsoluteFile(), e.getMessage());
                return false;
            }
        });
    }

}
