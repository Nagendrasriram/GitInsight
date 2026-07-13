package com.gitinsight.gitinsight_backend.service;

import com.gitinsight.gitinsight_backend.entity.Commit;
import com.gitinsight.gitinsight_backend.entity.GitRepository;
import com.gitinsight.gitinsight_backend.repository.CommitRepository;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class CommitExtractionService {

    private final CommitRepository commitRepository;

    public CommitExtractionService(CommitRepository commitRepository) {
        this.commitRepository = commitRepository;
    }

    @Transactional
    public void extractAndSaveCommits(GitRepository repositoryMetadata) {
        System.out.println("Starting commit extraction for: " + repositoryMetadata.getName());
        commitRepository.deleteByRepositoryId(repositoryMetadata.getId());        File localRepoDirectory = new File(repositoryMetadata.getLocalPath());
        List<Commit> commitsToSave = new ArrayList<>();

        // Open the existing local repository using JGit
        try (Git git = Git.open(localRepoDirectory)) {

            // git.log().call() fetches the entire commit history
            Iterable<RevCommit> gitLog = git.log().call();

            for (RevCommit jgitCommit : gitLog) {
                // Convert Git's raw Unix timestamp (seconds) into a modern Java LocalDateTime
                LocalDateTime commitDateTime = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(jgitCommit.getCommitTime()),
                        ZoneId.systemDefault()
                );

                // Build our database entity
                Commit ourCommit = Commit.builder()
                        .hash(jgitCommit.getName()) // JGit uses .getName() for the SHA-1 Hash
                        .author(jgitCommit.getAuthorIdent().getName())
                        .message(jgitCommit.getFullMessage())
                        .commitDate(commitDateTime)
                        .repository(repositoryMetadata) // Link it to the parent repository!
                        .build();

                commitsToSave.add(ourCommit);
            }

            // Batch save to PostgreSQL for high performance
            commitRepository.saveAll(commitsToSave);
            System.out.println("Successfully extracted and saved " + commitsToSave.size() + " commits.");

        } catch (Exception e) {
            throw new RuntimeException("Failed to extract commits from the local repository", e);
        }
    }
}
