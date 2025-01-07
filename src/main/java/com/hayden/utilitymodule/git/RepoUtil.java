package com.hayden.utilitymodule.git;


import com.hayden.utilitymodule.result.ClosableResult;
import com.hayden.utilitymodule.result.OneResult;
import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.error.SingleError;
import lombok.SneakyThrows;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.FS;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public interface RepoUtil {

    record GitInitError(String getMessage) implements SingleError {}
    record RepoUtilError(String getMessage) implements SingleError {}

    static ClosableResult<Git, GitInitError> initGit(Path path) {
        if (path.toFile().isDirectory() && !path.toFile().getName().endsWith(".git")) {
            path = path.resolve(".git");
        }

        if (!path.toFile().getName().endsWith(".git")) {
            return Result.tryErr(new GitInitError("Invalid git path: " + path));
        }

        final Path repoPath = path;

        if (repoPath.toFile().exists()) {
            return Result.tryFrom(() -> Git.open(repoPath.toFile()));
        }

        return Result.tryFrom(
                () -> Git.init().setGitDir(repoPath.toFile()).setInitialBranch("main")
                        .setFs(FS.detect())
                        .setDirectory(repoPath.toFile().getParentFile())
                        .call());
    }

    static Path getGitRepo() {
        var f= new File("");
        return getGitRepo(f);
    }

    static @NotNull Path getGitRepo(File f) {
        var p = f.toPath();

        if (p.resolve(".git").toFile().exists()) {
            return f.toPath().resolve(".git").toAbsolutePath();
        } else {
            while (f.getParentFile() != null && !f.getParentFile().toPath().resolve(".git").toFile().exists()) {
                var pf = f.getParentFile();
                if (pf.equals(f) || pf.equals(f.toPath().getRoot().toFile()))
                    break;
                else
                    f = pf;
            }

            if (f.toPath().resolve(".git").toFile().exists()) {
                return f.toPath().resolve(".git").toAbsolutePath();
            }
        }


        throw new RuntimeException("Could not find git repository");
    }

    // Helper method to find the last commit before the file was deleted
    @SneakyThrows
    static Result<RevCommit, RepoUtilError> getLastCommitBeforeDeletion(Git repository, String filePath) {
        // Assuming the file was deleted in the latest commit, we walk backwards to find the commit that last contained the file
        var commits = repository.log().addPath(filePath).call();
        for (RevCommit commit : commits) {
            return Result.ok(commit); // This is the last commit where the file exists
        }

        return Result.err(new RepoUtilError("Could not find commit containing %s".formatted(filePath)));
    }

    static Result<String, RepoUtilError> retrieveDeletedContent(String path, Git git) {
        return getLastCommitBeforeDeletion(git, path)
                .filterResult(Objects::nonNull)
                .flatMapResult(lastCommit -> treeWalkForPathInCommit(lastCommit, git, path))
                .flatMapResult(treeWalk -> {
                    // Step 3: Retrieve the file content from that commit
                    try {
                        if (treeWalk.next()) {
                            // Get the file content from the object id at that commit
                            try {
                                ObjectId fileId = treeWalk.getObjectId(0);
                                byte[] fileContent = git.getRepository().open(fileId).getBytes();
                                return Result.ok(new String(fileContent, StandardCharsets.UTF_8));
                            } catch (IOException e) {
                                return Result.err(new RepoUtilError("Failed to open file when retrieving deleted: %s.".formatted(e.getMessage())));
                            }
                        } else {
                            return Result.err(new RepoUtilError("Tree walk did not have next for %s".formatted(path)));
                        }
                    } catch (IOException e) {
                        return Result.err(new RepoUtilError("Failed to perform tree walk when retrieving deleted: %s.".formatted(e.getMessage())));
                    }
                });

        }

    static Result<TreeWalk, RepoUtil.RepoUtilError> treeWalkForPathInCommit(RevCommit lastCommit, Git git, String path) {
        TreeWalk treeWalk = new TreeWalk(git.getRepository());
        try {
            treeWalk.addTree(lastCommit.getTree());
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathFilter.create(path));
            return Result.ok(treeWalk);
        } catch (IOException e) {
            return Result.err(new RepoUtil.RepoUtilError("Could not do tree walk when retrieving deleted: %s.".formatted(e.getMessage())));
        }
    }

    static OneResult<RevCommit, RepoUtilError> getLatestCommit(Git repository) {
        return Result.<RevWalk, RepoUtilError>tryFrom(() -> new RevWalk(repository.getRepository()))
                .flatMapResult(rw -> {
                    try {
                        ObjectId head = repository.getRepository().resolve("HEAD");
                        RevCommit commit = rw.parseCommit(head);
                        return Result.ok(commit);
                    } catch(IOException e) {
                        return Result.err(new RepoUtilError(e.getMessage()));
                    }
                })
                .one();
    }

    static Result<List<DiffEntry>, RepoUtilError> retrieveDiffEntries(String childHash, String parentHash,
                                                                      Git git) {
        try (var reader = git.getRepository().newObjectReader()) {
            var oldTree = new CanonicalTreeParser();
            var r = git.getRepository().resolve("%s^{tree}".formatted(childHash));
            oldTree.reset(reader, r);
            var newTree = new CanonicalTreeParser();
            var p = git.getRepository().resolve("%s^{tree}".formatted(parentHash));
            newTree.reset(reader, p);
            var diffEntries = git.diff().setOldTree(oldTree).setNewTree(newTree).setContextLines(0).call();
            return Result.ok(diffEntries);
        } catch (GitAPIException | IOException e) {
            return Result.err(new RepoUtilError(e.getMessage()));
        }
    }


}