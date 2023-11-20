package com.hayden.utilitymodule.io;

import lombok.extern.slf4j.Slf4j;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@Slf4j
public class FileUtils {

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

}
