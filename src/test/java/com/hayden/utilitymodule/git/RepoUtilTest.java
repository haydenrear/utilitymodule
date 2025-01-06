package com.hayden.utilitymodule.git;

import org.assertj.core.util.Files;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class RepoUtilTest {

    @Test
    void initGit() {
        var newTemp = Files.newTemporaryFolder().toPath();
        RepoUtil.initGit(newTemp.resolve(".git"))
                .doOnClosable(git -> {
                    try {
                        File file = newTemp.resolve("txt.txt").toFile();
                        var found = file.createNewFile();
                        assertTrue(file.exists());
                        git.add().addFilepattern(".").call();
                        var statusCall = git.status().call();
                        var foundChanges = statusCall.getUncommittedChanges();
                        assertEquals(1, foundChanges.size());
                    } catch (IOException |
                             GitAPIException e) {
                        throw new AssertionError(e);
                    }
                });
    }

    @Test
    void getGitRepo() {
        var repo = RepoUtil.getGitRepo();
        assertThat(repo.toFile().exists()).isTrue();
        Assertions.assertThrows(RuntimeException.class, () -> RepoUtil.getGitRepo(Files.newTemporaryFolder()));
    }
}