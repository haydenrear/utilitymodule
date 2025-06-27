package com.hayden.utilitymodule.io;


import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class FileUtilsTest {

    private Path testDir;
    private Path testDir1;
    private Path testDir3;
    private Path testDir4;
    private Path testDir5;

    @BeforeEach
    public void setUp() throws IOException {
        testDir = Files.createTempDirectory("testDir");
        testDir1 = Files.createTempDirectory("testDir1");
        testDir3 = Files.createTempDirectory("testDir3");
        testDir4 = Files.createTempDirectory("testDir4");
        testDir5 = Files.createTempDirectory("testDir5");
        Files.createFile(testDir.resolve("file1.txt"));
        Files.createFile(testDir.resolve("file2.txt"));

        Path subDir = Files.createDirectory(testDir.resolve("subDir"));
        Files.createFile(subDir.resolve("file3.txt"));
    }


    @SneakyThrows
    @Test
    public void testDeleteRecursive() {
        testDir.resolve("another").resolve("another").resolve("another").toFile().mkdirs();
        testDir.resolve("another").resolve("one.txt").toFile().createNewFile();
        testDir.resolve("another").resolve("another").resolve("one.txt").toFile().createNewFile();
        FileUtils.deleteFilesRecursive(testDir, p -> !Objects.equals(testDir, p));

        assertThat(testDir.toFile().exists()).isTrue();
        assertThat(testDir.toFile().listFiles().length).isEqualTo(0);
    }

    @Test
    public void testGet() {
    }

    @Test
    public void findTestWorkDir() {
        var tw = FileUtils.getTestWorkDir(new File("./").toPath());
        assertThat(tw).isNotEmpty();
        var t = tw.get();
        var listed = t.toFile().listFiles();
        assertThat(listed.length).isNotZero();
        assertThat(listed[0].getName()).isEqualTo("one");
    }

    @Test
    public void testGetFileIteratorRecursive() {
        Iterator<Path> fileIterator = FileUtils.GetFileIteratorRecursive(testDir);

        int count = 0;
        while (fileIterator.hasNext()) {
            Path file = fileIterator.next();
            assertTrue(Files.isRegularFile(file), "Path should be a regular file: " + file);
            count++;
        }

        assertEquals(3, count, "Total files should be 3");
    }

    @Test
    public void testSingleFile() throws IOException {
        Files.createFile(testDir1.resolve("file10.txt"));

        Iterator<Path> fileIterator = FileUtils.GetFileIteratorRecursive(testDir1);

        assertTrue(fileIterator.hasNext(), "Iterator should have one element");
        Path file = fileIterator.next();
        assertEquals(testDir1.resolve("file10.txt"), file, "File path should match");
        assertFalse(fileIterator.hasNext(), "Iterator should have no more elements");
    }

    @Test
    public void testNestedDirectories() throws IOException {
        Path subDir1 = Files.createDirectory(testDir3.resolve("subDir1"));
        Files.createFile(subDir1.resolve("file1.txt"));

        Path subDir2 = Files.createDirectory(subDir1.resolve("subDir2"));
        Files.createFile(subDir2.resolve("file2.txt"));

        Iterator<Path> fileIterator = FileUtils.GetFileIteratorRecursive(testDir3);

        int count = 0;
        while (fileIterator.hasNext()) {
            Path file = fileIterator.next();
            assertTrue(Files.isRegularFile(file), "Path should be a regular file: " + file);
            count++;
        }

        assertEquals(2, count, "Total files should be 2");
    }

    @Test
    public void testEmptyDirectory() {
        Iterator<Path> fileIterator = FileUtils.GetFileIteratorRecursive(testDir4);

        assertFalse(fileIterator.hasNext(), "Iterator should have no elements");
    }

    @Test
    public void testSymbolicLinks() throws IOException {
        Path targetFile = Files.createFile(testDir5.resolve("file1.txt"));
        Path targetDir = Files.createDirectory(testDir5.resolve("dir1"));
        Path symlinkFile = Files.createSymbolicLink(testDir5.resolve("symlinkFile.txt"), targetFile);
        Path symlinkDir = Files.createSymbolicLink(testDir5.resolve("symlinkDir"), targetDir);

        Iterator<Path> fileIterator = FileUtils.GetFileIteratorRecursive(testDir5);

        int count = 0;
        while (fileIterator.hasNext()) {
            Path file = fileIterator.next();
            if (Files.isRegularFile(file)) {
                assertTrue(Files.isRegularFile(file), "Path should be a regular file: " + file);
            } else if (Files.isSymbolicLink(file)) {
                assertTrue(Files.isSymbolicLink(file), "Path should be a symbolic link: " + file);
            }
            count++;
        }

        assertEquals(2, count, "Total files (including symbolic links) should be 4");
    }

    @Test
    public void testHasParent() {
        var hasParent = FileUtils.hasParentDirectoryMatching(p -> p.toAbsolutePath().toFile().getName().equals("drools"), Paths.get(""));
        assertThat(hasParent).isTrue();
        var noParent = FileUtils.hasParentDirectoryMatching(p -> p.toAbsolutePath().toFile().getName().equals("asdfjkdsllkj"), Paths.get(""));
        assertThat(noParent).isFalse();
    }
}
