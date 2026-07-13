package com.gitinsight.gitinsight_backend.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Service
public class OwnershipService {

    public Map<String, Integer> analyzeFileOwnership(String localRepoPath, String targetFile) {
        Map<String, Integer> ownershipMap = new HashMap<>();

        try (Git git = Git.open(new File(localRepoPath))) {
            // JGit magic: Only fetch commits that modified this specific file
            Iterable<RevCommit> commits = git.log().addPath(targetFile).call();

            for (RevCommit commit : commits) {
                String author = commit.getAuthorIdent().getName();
                ownershipMap.put(author, ownershipMap.getOrDefault(author, 0) + 1);
            }
        } catch (Exception e) {
            System.err.println("Error reading file history: " + e.getMessage());
        }

        return ownershipMap;
    }
}