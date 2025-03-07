package com.hayden.utilitymodule.io;

import com.hayden.utilitymodule.result.ClosableResult;
import com.hayden.utilitymodule.result.error.SingleError;
import com.hayden.utilitymodule.result.Result;
import jakarta.annotation.Nonnull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.springframework.util.Assert;

import java.io.*;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CoderResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class FileUtils {

    public static File newTemporaryFolder() {
        String tempFileName = UUID.randomUUID().toString();
        return newFolder(temporaryFolderPath() + tempFileName);
    }

    public static String temporaryFolderPath() {
        return File.separator + System.getProperty("java.io.tmpdir");
    }

    public static File newFolder(String path) {
        File file = createFileIfPathIsNotANonEmptyDirectory(path);

        try {
            if (!file.mkdir()) {
                throw cannotCreateNewFile(path, "a file was found with the same path");
            } else {
                return file;
            }
        } catch (Exception e) {
            throw cannotCreateNewFile(path, e);
        }
    }

    public static File newFile(String path) {
        File file = createFileIfPathIsNotANonEmptyDirectory(path);

        try {
            if (!file.createNewFile()) {
                throw cannotCreateNewFile(path, "a file was found with the same path");
            } else {
                return file;
            }
        } catch (IOException e) {
            throw cannotCreateNewFile(path, (Exception)e);
        }
    }

    private static File createFileIfPathIsNotANonEmptyDirectory(String path) {
        File file = new File(path);
        if (file.isDirectory() && !ArrayUtils.isEmpty(file.list())) {
            throw cannotCreateNewFile(path, "a non-empty directory was found with the same path");
        } else {
            return file;
        }
    }

    private static UncheckedIOException cannotCreateNewFile(String path, String reason) {
        throw cannotCreateNewFile(path, reason, (Exception)null);
    }

    private static UncheckedIOException cannotCreateNewFile(String path, Exception cause) {
        throw cannotCreateNewFile(path, (String)null, cause);
    }

    private static UncheckedIOException cannotCreateNewFile(String path, String reason, Exception cause) {
        String message = String.format("Unable to create the new file %s", path);
        if (!StringUtils.isEmpty(reason)) {
            message = message + ": " + reason;
        }

        if (cause == null) {
            throw new RuntimeException(message);
        } else if (cause instanceof IOException) {
            throw new UncheckedIOException(message, (IOException)cause);
        } else {
            throw new RuntimeException(message, cause);
        }
    }

    public static File replaceHomeDir(Path homeDir, String subDir) {
        if (subDir.startsWith("~/"))  {
            return homeDir.resolve(subDir.replace("~/", "")).toFile();
        }

        return new File(subDir);
    }

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

    public static Result<Path, FileError> getPathFor(Path old, Path newPath, Map<String, String> replace) {
        return Result.fromOptOrErr(FileUtils.searchForFileRecursive(newPath, old.toFile().getName())
                .filter(p -> p.toFile().exists())
                .or(() -> {
                    var res = old.resolve(newPath);

                    if (res.toFile().exists())
                        return Optional.of(res);

                    if (old.isAbsolute()) {
                        Path next = old;
                        List<String> pathSegments = new ArrayList<>();
                        while (next.getParent() != null && !newPath.toAbsolutePath().startsWith(next.toAbsolutePath())) {
                            pathSegments.add(next.toFile().getName());
                            next = next.getParent();
                        }

                        Path toResolve = newPath;

                        for (var p : pathSegments.reversed().subList(1, pathSegments.size())) {
                            var maybeResolve = toResolve.resolve(replace.getOrDefault(p, p));
                            if (!maybeResolve.toFile().exists()) {
                                toResolve = toResolve.resolve(p);
                            } else {
                                toResolve = maybeResolve;
                            }
                        }

                        return Optional.of(toResolve);
                    }

                    return Optional.empty();

                }), () -> new FileError("Could not find file: %s.".formatted(newPath)));
    }

    public record FileError(String message) {}

    public static Result<Boolean, FileError> appendToFileRes(String data, Path file) {
        try {
            if (isFileInvalidNotCreatable(file)) {
                var f = "Failed to write to file %s. Could not create file".formatted(file.toFile());
                log.error(f);
                return Result.err(new FileError(f));
            }

            if (data != null)
                try (FileWriter fw = new FileWriter(file.toFile(), true)) {
                    fw.write(data);
                }

            return Result.ok(true);
        } catch (IOException e) {
            var f = "Failed to write to file: %s.".formatted(e.getMessage());
            return Result.err(new FileError(f));
        }
    }

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

    public static Optional<Path> searchForFileRecursive(Path path, String fileName) {
        var did = Optional.ofNullable(path.toFile().listFiles())
                .stream();

        return did.flatMap(Arrays::stream)
                .flatMap(p -> {
                    if (p.isFile() && p.getName().equals(fileName)) {
                        return Stream.of(p.toPath());
                    } else if (p.isDirectory()) {
                        var didDelete = searchForFileRecursive(p.toPath(), fileName);
                        if (didDelete.isPresent()) {
                            return didDelete.stream();
                        }
                    }

                    return Stream.empty();
                })
                .findAny();
    }

    public static boolean doOnFilesRecursive(Path path, Function<Path, Boolean> toDo, boolean parallel) {
        var did = Optional.ofNullable(path.toFile().listFiles())
                .stream();

        if (parallel)
            did = did.parallel();

        var value = did.flatMap(Arrays::stream)
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

        boolean didDeleteAll = value.stream().allMatch(Boolean::booleanValue);
        return didDeletePath && didDeleteAll;

    }

    public static boolean doOnFilesRecursive(Path path, Function<Path, Boolean> toDo) {
        return doOnFilesRecursive(path, toDo, false);
    }

    public static boolean doOnFilesRecursiveParallel(Path path, Function<Path, Boolean> toDo) {
        return doOnFilesRecursive(path, toDo, true);
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

    public static String randomFilename(String appendTo) {
        return "%s-%s" .formatted(UUID.randomUUID().toString().replaceAll("-", ""), appendTo);
    }

    public static Result<Boolean, FileError> doesFileMatchContent(File f, String content) {
        return FileUtils.readToLazyIterator(f)
                .map(iter -> {
                    int i = 0;
                    String[] array;
                    if (content == null)
                        array = new String[] {null};
                    else
                        array = content.split(System.lineSeparator());
                    while (iter.hasNext()) {
                        var first = iter.next();
                        var second = array[i];
                        if (!Objects.equals(first, second)) {
                            return false;
                        }
                        i += 1;
                    }


                    return true;
                })
                .mapError(se -> new FileError(se.getMessage()));
    }

    public static @Nonnull Result<Iterator<String>, SingleError> readToLazyIterator(File f) {
        if (!f.exists())
            return Result.err(SingleError.fromMessage("File %s did not exist.".formatted(f.getAbsolutePath())));

        return Result.<BufferedReader, SingleError>tryFrom(() -> Files.newBufferedReader(f.toPath()))
                .except(exc -> {
                    throw new RuntimeException(exc);
                })
                .map(bfr -> {
                    final boolean[] isClosed = {false};
                    return new Iterator<>() {

                        @SneakyThrows
                        @Override
                        public boolean hasNext() {
                            if (isClosed[0])
                                return false;
                            try {
                                if (!bfr.ready()) {
                                    bfr.close();
                                    ClosableResult.registerClosed(bfr);
                                    isClosed[0] = true;
                                    return false;
                                }
                            } catch (IOException e) {
                                log.error("{}", SingleError.parseStackTraceToString(e));
                                isClosed[0] = true;
                                return false;
                            }

                            return true;
                        }

                        @SneakyThrows
                        @Override
                        public String next() {
                            try {
                                if (hasNext())
                                    return bfr.readLine();
                            } catch (IOException e) {
                                if (e instanceof CharacterCodingException c) {
                                    log.debug("Found character coding exception: {}", SingleError.parseStackTraceToString(c));
                                } else {
                                    log.error("{}", SingleError.parseStackTraceToString(e));
                                }
                            }

                            isClosed[0] = true;
                            return null;
                        }
                    };
                });

    }

    public static boolean hasParentDirectoryMatching(Predicate<Path> toMatch, Path starting) {
        starting = starting.toAbsolutePath();
        if (toMatch.test(starting))
            return true;

        if (isRoot(starting))
            return false;

        while (!isRoot(starting)) {
            starting = starting.getParent();
            if (toMatch.test(starting))
                return true;
        }

        return false;
    }

    public static boolean isRoot(Path starting) {
        return starting.getParent() == null || starting.getParent().equals(starting) || starting.getParent().equals(starting.getRoot());
    }

}
