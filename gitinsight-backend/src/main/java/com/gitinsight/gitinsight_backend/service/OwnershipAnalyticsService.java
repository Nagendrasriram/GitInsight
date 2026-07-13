package com.gitinsight.gitinsight_backend.service;

import com.gitinsight.gitinsight_backend.entity.GitRepository;
import com.gitinsight.gitinsight_backend.entity.Ownership;
import com.gitinsight.gitinsight_backend.repository.OwnershipRepository;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OwnershipAnalyticsService {

    private final OwnershipRepository ownershipRepository;

    public OwnershipAnalyticsService(OwnershipRepository ownershipRepository) {
        this.ownershipRepository = ownershipRepository;
    }

    @Transactional
    public void calculateFileOwnership(GitRepository repository) {
        System.out.println("Calculating File Ownership for: " + repository.getName());
        File localRepoDir = new File(repository.getLocalPath());

        // This Map acts as our Ledger. Format: FilePath -> (AuthorName -> ChangeCount)
        Map<String, Map<String, Integer>> fileOwnershipLedger = new HashMap<>();

        // We open three JGit tools at once using a Try-With-Resources block
        try (Git git = Git.open(localRepoDir);
             RevWalk walk = new RevWalk(git.getRepository());
             DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {

            diffFormatter.setRepository(git.getRepository());
            Iterable<RevCommit> commits = git.log().call();

            // Time Travel: Loop through every commit in history
            for (RevCommit commit : commits) {
                String authorName = commit.getAuthorIdent().getName();

                // Step 1: Get the Previous Commit's Tree (The Parent)
                AbstractTreeIterator parentTree;
                if (commit.getParentCount() > 0) {
                    RevCommit parent = walk.parseCommit(commit.getParent(0).getId());
                    parentTree = new CanonicalTreeParser(null, git.getRepository().newObjectReader(), parent.getTree().getId());
                } else {
                    parentTree = new EmptyTreeIterator(); // The very first commit has no parent!
                }

                // Step 2: Get the Current Commit's Tree
                AbstractTreeIterator currentTree = new CanonicalTreeParser(null, git.getRepository().newObjectReader(), commit.getTree().getId());

                // Step 3: Compare them to find out what files changed
                List<DiffEntry> diffs = diffFormatter.scan(parentTree, currentTree);

                // Step 4: Record the changes in our Ledger
                for (DiffEntry diff : diffs) {
                    String filePath = diff.getNewPath();

                    // If a file was completely deleted, JGit calls it DEV_NULL. We ignore these.
                    if (filePath.equals(DiffEntry.DEV_NULL)) {
                        continue;
                    }

                    // Add 1 to the author's score for this specific file
                    fileOwnershipLedger.putIfAbsent(filePath, new HashMap<>());
                    Map<String, Integer> authorScores = fileOwnershipLedger.get(filePath);
                    authorScores.put(authorName, authorScores.getOrDefault(authorName, 0) + 1);
                }
            }

            // Step 5: Convert our complex Map Ledger into simple Database Entities
            List<Ownership> ownershipRecordsToSave = new ArrayList<>();
            for (Map.Entry<String, Map<String, Integer>> fileEntry : fileOwnershipLedger.entrySet()) {
                String filePath = fileEntry.getKey();

                for (Map.Entry<String, Integer> authorEntry : fileEntry.getValue().entrySet()) {
                    ownershipRecordsToSave.add(Ownership.builder()
                            .filePath(filePath)
                            .contributorName(authorEntry.getKey())
                            .changeCount(authorEntry.getValue())
                            .repository(repository)
                            .build());
                }
            }

            // Batch save to PostgreSQL
            ownershipRepository.saveAll(ownershipRecordsToSave);
            System.out.println("Successfully calculated ownership for " + ownershipRecordsToSave.size() + " file-author pairs.");

        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate file ownership via JGit Tree Walk", e);
        }
    }
}