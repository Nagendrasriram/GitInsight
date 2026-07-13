package com.gitinsight.gitinsight_backend.services;

import org.eclipse.jgit.api.Git;
import org.springframework.stereotype.Service;
import java.io.File;

@Service
public class GitService {

    public String cloneRepository(String url) throws Exception {
        // Create a unique temporary directory for this specific repo
        String tempDir = System.getProperty("java.io.tmpdir") + File.separator + "gitinsight_" + System.currentTimeMillis();
        File localRepoDir = new File(tempDir);

        System.out.println("⏳ Cloning repository to: " + tempDir);

        // This command actually downloads the files from GitHub
        try (Git git = Git.cloneRepository()
                .setURI(url)
                .setDirectory(localRepoDir)
                .call()) {
            System.out.println("✅ Cloning complete.");
        }

        return tempDir; // Return the path so the Indexer knows where to look
    }
}