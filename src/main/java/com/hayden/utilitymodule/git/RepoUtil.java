package com.hayden.utilitymodule.git;


import com.hayden.utilitymodule.io.ArchiveUtils;
import com.hayden.utilitymodule.io.FileUtils;
import com.hayden.utilitymodule.result.ClosableResult;
import com.hayden.utilitymodule.result.OneResult;
import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.error.SingleError;
import lombok.SneakyThrows;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.NotTreeFilter;
import org.eclipse.jgit.treewalk.filter.OrTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.FS;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;


public interface RepoUtil {

    Logger log = LoggerFactory.getLogger(RepoUtil.class);

    static CanonicalTreeParser getForRef(String pattern, Repository repository) throws IOException {
        try (RevWalk revWalk = new RevWalk(repository)) {
            ObjectId head = repository.resolve(pattern);
            RevCommit headCommit = revWalk.parseCommit(head);
            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            treeParser.reset(repository.newObjectReader(), headCommit.getTree());
            return treeParser;
        }
    }

    static Optional<RepoUtilError> doReset(Git git) throws GitAPIException {
        try {
            log.debug("Doing reset");
            ObjectId head = git.getRepository().resolve("HEAD^1");
            git.reset().setMode(ResetCommand.ResetType.SOFT).setRef(head.name()).call();
            return Optional.empty();
        } catch (IOException e) {
            return Optional.of(new RepoUtilError(Arrays.toString(e.getStackTrace())));
        }
    }

    static void doTempCommit(Git holder) throws GitAPIException {
        holder.add().addFilepattern(".").call();
        var s = holder.status().call().getUncommittedChanges();
        var toDelete = holder.status().call().getUncommittedChanges()
                .stream()
                .filter(Predicate.not(uncommitted -> holder.getRepository().getDirectory().getParentFile().toPath().resolve(uncommitted).toFile().exists()))
                .toList();

        for (var d : toDelete) {
            holder.rm().addFilepattern(d).call();
        }

        holder.add().addFilepattern(".").call();

        var committed = holder.commit().setMessage("temp").call();
        log.debug("Temp commit: {}, {}", committed, s);
    }

    static <T> @NotNull OneResult<T, RepoUtilError> doInsideCommitStaged(Git git, Supplier<T> toDo) {
        try {
            git.add().addFilepattern(".").call();
            git.commit().setMessage("temp").call();
            var retrieved = toDo.get();
            return Result.ok(retrieved);
        } catch (GitAPIException e) {
            return Result.err(new RepoUtilError(e));
        } finally {
            try {
                git.reset().setMode(ResetCommand.ResetType.SOFT).setRef("HEAD~1").call();
            } catch (GitAPIException e) {
                log.error("Error applying stash or reset .", e);
            }
        }
    }

    static <T> @NotNull OneResult<T, RepoUtilError> doInsideStash(Git git, Supplier<T> toDo) {
        return doInsideReset(git, "HEAD", toDo);
    }

    static <T> @NotNull OneResult<T, RepoUtilError> doInsideReset(Git git, String resetTo, Supplier<T> toDo) {
        try {
            git.add().addFilepattern(".").call();
            // save staged changes in case have any
            var created = git.stashCreate().call();
            ObjectId head = git.getRepository().resolve("HEAD");
            return Result.<ObjectId, RepoUtilError>fromOpt(Optional.ofNullable(head))
                    .map(AnyObjectId::name)
                    .flatMapResult(toResetTo -> {
                        try {
                            git.reset().setRef(resetTo)
                                    .setMode(ResetCommand.ResetType.HARD)
                                    .call();
                            var retrieved = toDo.get();
                            return Result.ok(retrieved);
                        } catch (GitAPIException e) {
                            return Result.err(new RepoUtilError(e));
                        } finally {
                            try {
                                git.reset().setRef(toResetTo).setMode(ResetCommand.ResetType.HARD).call();
                                if (created != null) {
                                    git.stashApply().call();
                                }
                            } catch (GitAPIException e) {
                                log.error("Error applying stash or reset .", e);
                            }
                        }
                    })
                    .one();
        } catch (IOException | GitAPIException e) {
            return Result.err(new RepoUtilError(e));
        }
    }

    static @NotNull OneResult<List<DiffEntry>, RepoUtilError> retrieveStagedChanges(Git git) {
        try {
            doTempCommit(git);
            var gd = git.diff()
                    .setOldTree(getForRef("HEAD^1", git.getRepository()))
                    .setNewTree(getForRef("HEAD", git.getRepository()))
                    .call();

            return Result.fromOpt(Optional.ofNullable(gd), doReset(git)).one();
        } catch (GitAPIException | IOException e) {
            return Result.err(new RepoUtilError("Failed when retrieving staged diff: %s.".formatted(e.getMessage())));
        }
    }

    record GitInitError(String getMessage) implements SingleError {}

    record RepoUtilError(String getMessage) implements SingleError {
        public RepoUtilError(Throwable getMessage) {
            this(SingleError.parseStackTraceToString(getMessage));
        }
    }

    static Result<Path, RepoUtilError> cloneIfRemote(String url, String branchName, File gitDir) {
        if (url.startsWith("http") || url.startsWith("git") || url.startsWith("ssh")) {
            return RepoUtil.cloneRepo(gitDir, url, branchName)
                    .mapError(gitInitError -> new RepoUtilError("Failed to clone git repo: %s.".formatted(gitInitError.getMessage())))
                    .map(git -> gitDir.toPath());
        }

        return returnEmptyOrErrIfNotExists(url);
    }

    static Result<Path, RepoUtilError> cloneIfRemote(String url, String branchName) {
        return cloneIfRemote(url, branchName, FileUtils.newTemporaryFolder());
    }

    static Result<Path, RepoUtilError> decompressIfArchive(String url) {
        if (url.endsWith(".tar")) {
            if (!new File(url).exists()) {
                return Result.err(new RepoUtilError("Repo archive %s did not exist.".formatted(url)));
            }
            var tempDir = FileUtils.newTemporaryFolder();
            Path tarPath = Paths.get(url);
            Path unzippedPath = tempDir.toPath();
            var unzipped = ArchiveUtils.prepareTestRepos(tarPath.getParent(), unzippedPath, tarPath.getFileName().toString());
            return unzipped
                    .mapError(se -> new RepoUtilError(se.getMessage()))
                    .map(unzippedFiles -> unzippedPath);
        }

        return returnEmptyOrErrIfNotExists(url);
    }

    static Result<Path, RepoUtilError> doDecompressCloneRepo(String url, String branchName) {
        return decompressIfArchive(url)
                .dropErr()
                .one()
                .or(() -> cloneIfRemote(url, branchName))
                .dropErr()
                .one()
                .or(() -> returnPathOrErrIfNotExists(url));
    }

    private static @NotNull Result<Path, RepoUtilError> returnEmptyOrErrIfNotExists(String url) {
        var f = new File(url);

        if (f.exists())
            return Result.empty();

        return Result.err(new RepoUtilError("Failed to clone git repo - %s did not exist.".formatted(url)));
    }

    private static @NotNull Result<Path, RepoUtilError> returnPathOrErrIfNotExists(String url) {
        var f = new File(url);

        if (f.exists())
            return Result.ok(f.toPath());

        return Result.err(new RepoUtilError("Failed to clone git repo - %s did not exist.".formatted(url)));
    }


    static Result<Git, RepoUtilError> cloneRepo(File gitDir, String toClone, String branch) {
        return Result.<Git, RepoUtilError>tryFrom(() ->
                Git.cloneRepository().setFs(FS.detect())
                        .setDirectory(gitDir)
                        .setURI(toClone)
                        .setBranch(branch)
                        .call())
                .flatExcept(exc -> Result.err(new RepoUtilError(exc)));
    }

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

    static Map.Entry<Ref, String> retrieveBranch(String branch, Git git) throws GitAPIException {
        var branchStart = git.branchList().call().stream()
                .filter(r -> r.getName().contains(branch))
                .map(r -> Map.entry(r, branch))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Branch %s not found.".formatted(branch)));
        return branchStart;
    }

    static OneResult<RevCommit, RepoUtilError> getLatestCommit(Git git, String branch) {
        try {
            var branchStart = retrieveBranch(branch, git);
            Iterator<RevCommit> commits = git.log().add(branchStart.getKey().getObjectId()).call().iterator();
            return Result.ok(commits.next());
        } catch (GitAPIException |
                 IncorrectObjectTypeException |
                 MissingObjectException e) {
            return Result.err(new RepoUtilError(SingleError.parseStackTraceToString(e)));
        }
    }

    static Result<List<DiffEntry>, RepoUtilError> retrieveDiffEntries(String childHash, String parentHash,
                                                                      Git git,
                                                                      Set<String> excludePattern) {
        try (var reader = git.getRepository().newObjectReader()) {
            var oldTree = new CanonicalTreeParser();
            var r = git.getRepository().resolve("%s^{tree}".formatted(childHash));
            oldTree.reset(reader, r);
            var newTree = new CanonicalTreeParser();
            var p = git.getRepository().resolve("%s^{tree}".formatted(parentHash));
            newTree.reset(reader, p);
            Path parent = git.getRepository().getDirectory().toPath().getParent();
            var diffEntries = git.diff().setOldTree(oldTree).setNewTree(newTree)
                    .setPathFilter(
                            NotTreeFilter.create(
                                    OrTreeFilter.create(
                                            excludePattern.stream()
                                                    .map(w -> new WildcardPathFilter(w, parent))
                                                    .toArray(WildcardPathFilter[]::new))
                            ))
                    .setContextLines(0).call();
            return Result.ok(diffEntries);
        } catch (GitAPIException | IOException e) {
            return Result.err(new RepoUtilError(e.getMessage()));
        }
    }


}