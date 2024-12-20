package com.hayden.utilitymodule.io;

import com.hayden.utilitymodule.result.error.SingleError;
import com.hayden.utilitymodule.result.Result;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
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

    public record FileError(String message) {}

    public static Result<Boolean, FileError> writeToFileRes(String data, Path file) {
        try {
            if (isFileInvalidNotCreatable(file)) {
                var f = "Failed to write to file %s. Could not create file".formatted(file.toFile());
                log.error(f);
                return Result.err(new FileError(f));
            }

            if (data != null)
                try (FileWriter fw = new FileWriter(file.toFile())) {
                    fw.write(data);
                }

            return Result.ok(true);
        } catch (IOException e) {
            var f = "Failed to write to file: %s.".formatted(e.getMessage());
            return Result.err(new FileError(f));
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

    public static boolean isEmptyDirectory(File[] next, int filePointer) {
        return !next[filePointer].isFile()
                && Optional.ofNullable(next[filePointer].listFiles())
                .map(f -> f.length).map(l -> l == 0).orElse(true);
    }

    public static <T> T doOverFilePathLines(Path file, BiConsumer<String, AtomicReference<T>> toDo, AtomicReference<T> update) {
        if (file.toFile().isFile()) {
            try(
                    FileReader fr = new FileReader(file.toFile());
                    BufferedReader bf = new BufferedReader(fr)
            ) {
                bf.lines().forEach(l -> toDo.accept(l, update));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return update.get();
    }

    public static Iterator<Path> GetFileIteratorRecursive(Path path) {
        record FilePointer(File[] file, int pointer) {}

        final File[][] next = {path.toFile().listFiles()};
        Stack<FilePointer> parent = new Stack<>();
        final AtomicInteger[] filePointer = {new AtomicInteger(0)};

        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                boolean isValidFilePointer = filePointer[0].get() < next[0].length;
                if (isValidFilePointer && isEmptyDirectory(next[0], filePointer[0].get()))
                    return false;
                if (isValidFilePointer)
                    return true;
                else {
                    while (!parent.isEmpty()) {
                        FilePointer top = parent.peek();
                        if (top.pointer < top.file.length) {
                            top = parent.pop();
                            filePointer[0] = new AtomicInteger(top.pointer);
                            next[0] = top.file;
                            if (next[0].length > filePointer[0].get()
                                    && isEmptyDirectory(next[0], filePointer[0].get())) {
                                continue;
                            }
                            return true;
                        } else {
                            parent.pop();
                        }
                    }
                }

                return false;
            }

            @Override
            public Path next() {
                var fp = filePointer[0].get();
                if (next[0][fp].isFile()) {
                    return next[0][filePointer[0].getAndIncrement()].toPath();
                } else {
                    parent.add(new FilePointer(next[0], filePointer[0].incrementAndGet()));
                    next[0] = next[0][fp].listFiles();
                    filePointer[0] = new AtomicInteger(0);
                    if (hasNext()) {
                        return next();
                    } else {
                        return null;
                    }
                }
            }
        };
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
                Assert.isTrue(isEmpty(p), "Was not empty directory when deleting recursively.");
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

    public static @Nonnull Result<String, SingleError> readToString(File f) {
        try (
                FileReader fileReader = new FileReader(f);
                BufferedReader bf = new BufferedReader(fileReader)
        ) {
            return Result.ok(bf.lines().collect(Collectors.joining(System.lineSeparator())));
        } catch (IOException e) {
            return Result.err(SingleError.fromE(e));
        }
    }
}
