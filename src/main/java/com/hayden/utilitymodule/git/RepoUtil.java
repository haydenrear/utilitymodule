package com.hayden.utilitymodule.git;


import java.io.File;
import java.nio.file.Path;

public interface RepoUtil {


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
