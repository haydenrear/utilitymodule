package com.hayden.utilitymodule.io;

import com.hayden.utilitymodule.result.ClosableResult;
import com.hayden.utilitymodule.result.error.SingleError;
import com.hayden.utilitymodule.result.Result;
import jakarta.annotation.Nonnull;
import jakarta.validation.constraints.Null;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.CharacterCodingException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class FileUtils {

    public static boolean isNotSelfNorStrictSubPath(Path child, Path parent) {
        return !Objects.equals(child, parent) && !isSelfOrStrictSubPath(child, parent);
    }

    public static boolean isSelfOrStrictSubPath(Path child, Path parent) {
        return Objects.equals(child, parent) || isStrictSubPath(child, parent);
    }

    public static boolean isStrictSubPath(Path child, Path parent) {
        return (
                !child.equals(parent) &&
                child.normalize().startsWith(parent.normalize()) &&
                child.getNameCount() > parent.getNameCount()
        );
    }

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
            throw cannotCreateNewFile(path, e);
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

    public static Result<Path, FileError> getPathFor(Path filePathDir, Path rootDir, Map<String, String> replace) {
        var result = rootDir.resolve(filePathDir);

        if (result.toFile().exists())
            return Result.ok(result);

        if (rootDir.isAbsolute()) {
            var root = rootDir.toAbsolutePath().toFile().getAbsolutePath();
            for (var entry : replace.entrySet()) {
                root = root.replaceAll(entry.getKey(), entry.getValue());
            }
            rootDir = Paths.get(root);
        }

        if (filePathDir.isAbsolute()) {
            var root = filePathDir.toAbsolutePath().toFile().getAbsolutePath();
            for (var entry : replace.entrySet()) {
                root = root.replaceAll(entry.getKey(), entry.getValue());
            }
            filePathDir = Paths.get(root);
        }

        Path finalFilePathDir = filePathDir;
        Path finalRootDir = rootDir;
        return Result.fromOptOrErr(FileUtils.searchForFileRecursive(rootDir, filePathDir.toFile().getName())
                .filter(p -> p.toFile().exists())
                .or(() -> {
                    var res = finalRootDir.resolve(finalFilePathDir);
                    return Optional.of(res);
                }), () -> new FileError("Could not find file: %s, %s.".formatted(finalRootDir, finalFilePathDir)));
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

    /**
     * Searches upwards from the given starting directory to find the closest
     * parent directory that contains a file with the specified name.
     *
     * @param startPath The starting directory path.
     * @param targetFileName The name of the file to search for.
     * @return The File object of the directory that contains the file, or null if not found.
     */
    public static File findClosestParentWithFile(Path startPath, Predicate<String> targetFileName) {
        var p = searchForRecursive(startPath, f -> targetFileName.test(f.getName()));

        if (p.isPresent())
            return p.get().toFile();

        Path current = startPath.toAbsolutePath();
        while (current != null) {
            File[] matchingFiles = current.toFile().listFiles((dir, name) -> targetFileName.test(name));
            if (matchingFiles != null && matchingFiles.length > 0) {
                return current.toFile();
            }
            current = current.getParent();
        }

        return null;
    }

    public static Optional<Path> getTestWorkDir() {
        return getTestWorkDir(new File("./").toPath()) ;
    }

    public static Optional<Path> getTestWorkDir(Path path) {
        return searchForRecursive(path, f -> f.isDirectory() && Objects.equals(f.getName(), "test_work"));
    }

    public static Optional<Path> searchForRecursive(Path path, Predicate<File> fileName) {

        if (fileName.test(path.toFile()))
            return Optional.of(path);

        var did = Optional.ofNullable(path.toFile().listFiles())
                .stream();

        return did.flatMap(Arrays::stream)
                .flatMap(p -> {
                    if (fileName.test(p)) {
                        return Stream.of(p.toPath());
                    } else if (p.isDirectory()) {
                        var didDelete = searchForRecursive(p.toPath(), fileName);
                        if (didDelete.isPresent()) {
                            return didDelete.stream();
                        }
                    }

                    return Stream.empty();
                })
                .findAny();
    }

    public static Optional<Path> searchForFileRecursive(Path path, String fileName) {
        return searchForRecursive(path, p -> p.isFile() && fileName.equals(p.getName()));
    }

    public static Path findVisit(Path source, Predicate<Path> found, Predicate<Path> continueWalking) {
        AtomicReference<Path> result = new AtomicReference<>();
        try {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (found.test(dir)) {
                        result.set(dir);
                        return FileVisitResult.TERMINATE;
                    }
                    if (continueWalking.test(dir))
                        return FileVisitResult.CONTINUE;

                    return FileVisitResult.SKIP_SUBTREE;
                }

                @Override
                public FileVisitResult visitFile(Path dir, BasicFileAttributes attrs) {
                    if (found.test(dir)) {
                        result.set(dir);
                        return FileVisitResult.TERMINATE;
                    }
                    if (continueWalking.test(dir))
                        return FileVisitResult.CONTINUE;

                    return FileVisitResult.SKIP_SUBTREE;
                }
            });
        } catch (IOException e) {
            log.error("Failed to visit file: {}.", e.getMessage());
        }
        return result.get();
    }


    public static void copyAll(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetPath = target.resolve(source.relativize(dir));
                Files.createDirectories(targetPath);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetPath = target.resolve(source.relativize(file));
                Files.copy(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static boolean doOnFilesRecursive(Path path, Function<Path, Boolean> toDo) {
        return doOnFilesRecursive(path, toDo, r -> false);
    }

    @SneakyThrows
    public static boolean doOnFilesRecursive(Path path, Function<Path, Boolean> toDo, Predicate<Path> skipTree) {
        AtomicBoolean result = new AtomicBoolean(true);
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) {
                if (file.toFile().isFile()) {
                    if (!toDo.apply(file)) {
                        result.set(false);
                    }
                }

                if (skipTree.test(file)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                return FileVisitResult.CONTINUE;
            }
        });

        return result.get();
    }

    @SneakyThrows
    public static boolean doOnFilesRecursiveParallel(Path path, Function<Path, Boolean> toDo) {
        try(var f = Files.walk(path)) {
            return f.parallel().allMatch(toDo::apply) ;
        }
    }

    /**
     * returns AutoClosable - must use in try with resources or close explicitly.
     * @param path
     * @return
     */
    public static Stream<Path> getFilesRecursive(Path path) {
        try {
            return Files.walk(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    @SneakyThrows
    public static Iterator<Path> fileIteratorRecursive(Path path) {
        Stream<Path> walk = Files.walk(path);
        var f = walk.iterator();
        AtomicBoolean isClosed = new AtomicBoolean(false);
        return new Iterator<>() {

            @Override
            public boolean hasNext() {
                if (isClosed.get())
                    return false;

                var n = f.hasNext();

                if (n)
                    return true;

                isClosed.set(true);
                walk.close();
                return false;
            }

            @Override
            public @Nullable Path next() {
                try {
                    var p = f.next();
                    return p;
                } catch (NoSuchElementException e) {
                    isClosed.set(true);
                    walk.close();
                    throw e;
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
        return deleteFilesRecursive(path, s -> true) ;
    }

    public static boolean deleteFilesRecursive(Path target, Predicate<Path> doDelete) {
        return deleteFilesRecursive(target, doDelete, r -> false);
    }

    public static boolean deleteFilesRecursive(Path target, Predicate<Path> doDelete, Predicate<Path> skipTree) {
        try {
            Files.walkFileTree(target,  new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (Files.isSymbolicLink(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (dir.toFile().isDirectory() && skipTree.test(dir))  {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NotNull FileVisitResult postVisitDirectory(@NotNull Path dir, @Nullable IOException exc) throws IOException {
                    if (doDelete.test(dir))
                        Files.deleteIfExists(dir);

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if ((file.toFile().isFile() || Files.isSymbolicLink(file)) && doDelete.test(file)) {
                        Files.deleteIfExists(file);
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (IOException e) {
            return false;
        }
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

    public record LazyIterator(@Delegate Iterator<String> iter, BufferedReader reader, boolean[] isClosed) {

        public void doClose() throws IOException {
            reader.close();
            ClosableResult.registerClosed(reader);
            isClosed[0] = true;
        }

    }

    public static @Nonnull Result<LazyIterator, SingleError> readToLazyIterator(File f) {
        if (!f.exists())
            return Result.err(SingleError.fromMessage("File %s did not exist.".formatted(f.getAbsolutePath())));

        return Result.<BufferedReader, SingleError>tryFrom(() -> Files.newBufferedReader(f.toPath()))
                     .except(exc -> {
                         throw new RuntimeException(exc);
                     })
                     .map(bfr -> {
                         final boolean[] isClosed = {false};
                         return new LazyIterator(new Iterator<>() {

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
                                 } catch (
                                         IOException e) {
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
                                 } catch (
                                         IOException e) {
                                     if (e instanceof CharacterCodingException c) {
                                         log.debug("Found character coding exception: {}", SingleError.parseStackTraceToString(c));
                                     } else {
                                         log.error("{}", SingleError.parseStackTraceToString(e));
                                     }
                                 }

                                 isClosed[0] = true;
                                 return null;
                             }
                         },
                                 bfr,
                                 isClosed);
                     });

    }

    public static Path retrieveParentDirectoryMatching(Predicate<Path> toMatch, Path starting) {
        starting = starting.toAbsolutePath();
        if (toMatch.test(starting))
            return starting;

        if (isRoot(starting))
            return null;

        while (!isRoot(starting)) {
            starting = starting.getParent();
            if (toMatch.test(starting))
                return starting;
        }

        return null;
    }

    public static boolean hasParentDirectoryMatching(Predicate<Path> toMatch, Path starting) {
        return retrieveParentDirectoryMatching(toMatch, starting.toAbsolutePath()) != null;
    }

    public static boolean isRoot(Path starting) {
        return starting.getParent() == null || starting.getParent().equals(starting) || starting.getParent().equals(starting.getRoot());
    }

}
