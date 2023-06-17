package com.hayden.utilitymodule.io;

import lombok.extern.slf4j.Slf4j;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

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

}
