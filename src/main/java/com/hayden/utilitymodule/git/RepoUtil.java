package com.hayden.utilitymodule.git;


import com.hayden.utilitymodule.result.ClosableResult;
import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.error.SingleError;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.nio.file.Path;

public interface RepoUtil {

    record GitInitError(String getMessage) implements SingleError {}

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
        var p = f.toPath();

        if (p.resolve(".git").toFile().exists()) {
            return f.toPath().resolve(".git").toAbsolutePath();
        } else {
            while (!f.getParentFile().toPath().resolve(".git").toFile().exists()) {
                f = f.getParentFile();
            }

            if (f.toPath().resolve(".git").toFile().exists()) {
                return f.toPath().resolve(".git").toAbsolutePath();
            }
        }


        throw new RuntimeException("Could not find git repository");
    }

}
