package com.hayden.utilitymodule.io;

import com.hayden.utilitymodule.result.error.Error;
import com.hayden.utilitymodule.result.Result;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;

import java.io.*;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class FileUtils {

    public static InputStream getResourceAsStream(String value) {
        return FileUtils.class.getClassLoader().getResourceAsStream(value);
    }

    public static boolean writeBytesToFile(byte[] data, Path file) {
        try {
            if (isFileInvalidNotCreatable(file)) {
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
            if (isFileInvalidNotCreatable(file)) {
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

    private static boolean isFileInvalidNotCreatable(Path file) throws IOException {
        createFile(file);
        return !file.toFile().exists();
    }

    private static void createDirs(Path file) {
        file.getParent().toFile().mkdirs();
    }

    private static void createFile(Path file) throws IOException {
        if (file.toFile().exists())
            return;
        createDirs(file);
        file.toFile().createNewFile();

    }

    public static boolean doOnFilesRecursive(Path path, Function<Path, Boolean> toDo) {
        var did = Optional.ofNullable(path.toFile().listFiles())
                .stream()
                .flatMap(Arrays::stream)
                .map(p -> {
                    if (p.isFile()) {
                        return toDo.apply(p.toPath());
                    } else if (p.isDirectory()) {
                        boolean didDelete = doOnFilesRecursive(p.toPath(), toDo);
                        boolean deleteDir = toDo.apply(p.toPath());
                        return didDelete && deleteDir;
                    }
                    return false;
                })
                .collect(Collectors.toSet());
        Boolean didDeletePath = toDo.apply(path);
        boolean didDeleteAll = did.stream().allMatch(Boolean::booleanValue);
        return didDeletePath && didDeleteAll;
    }

    public static Stream<Path> GetFilesRecursive(Path path) {
        Stream.Builder<Path> builder = Stream.builder();
        doOnFilesRecursive(path, (p) -> {
            builder.add(p);
            return true;
        });
        return builder.build();
    }

    public static Stream<File> getFileStream(Path path) {
        return Optional.ofNullable(path.toFile().listFiles())
                .stream()
                .flatMap(Arrays::stream);
    }

    public static boolean isEmpty(Path path) {
        return getFileStream(path).findAny().isEmpty();
    }

    public static boolean deleteFilesRecursive(Path path) {
        return doOnFilesRecursive(path, p -> {
            if (p.toFile().isDirectory()) {
                Assert.assertTrue(isEmpty(p));
                if (!p.toFile().delete()) {
                    logNotDeleted(p);
                    return false;
                }
                return true;
            } else if (p.toFile().isFile() && !p.toFile().delete()) {
                logNotDeleted(p);
            } else {
                return false;
            }
            return false;
        });
    }

    private static void logNotDeleted(Path p) {
        log.error("Could not delete {}.", p);
    }

    public static @Nonnull Result<String, Error> readToString(File f) {
        try (
                FileReader fileReader = new FileReader(f);
                BufferedReader bf = new BufferedReader(fileReader)
        ) {
            return Result.ok(bf.lines().collect(Collectors.joining(System.lineSeparator())));
        } catch (IOException e) {
            return Result.err(e);
        }
    }
}
