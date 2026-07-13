package com.gitinsight.gitinsight_backend.service;
import com.gitinsight.gitinsight_backend.entity.GitRepository;
import com.gitinsight.gitinsight_backend.repository.GitRepositoryRepository;
//import com.gitinsight.entity.GitRepository;
//import com.gitinsight.repository.GitRepositoryRepository;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

@Service
public class GitCloningService {

    private final GitRepositoryRepository repositoryDataAccessor;

    public GitCloningService(GitRepositoryRepository repositoryDataAccessor) {
        this.repositoryDataAccessor = repositoryDataAccessor;
    }

    @Transactional
    public GitRepository cloneAndRegister(String repositoryUrl) {
        // Check if we have already analyzed this repository previously
        var existingRepo = repositoryDataAccessor.findByUrl(repositoryUrl);
        if (existingRepo.isPresent()) {
            return existingRepo.get();
        }

        String repoName = extractRepositoryName(repositoryUrl);
        Path targetLocalStoragePath;

        try {
            // Create a unique temporary folder on the operating system filesystem
            targetLocalStoragePath = Files.createTempDirectory("gitinsight_" + repoName + "_");
        } catch (IOException exception) {
            throw new RuntimeException("Failed to allocate local server storage space for the repository cloning process", exception);
        }

        // Execute the physical JGit clone command targeting the remote server
        try (Git gitConnectionObject = Git.cloneRepository()
                .setURI(repositoryUrl)
                .setDirectory(targetLocalStoragePath.toFile())
                .setCloneAllBranches(false)
                .setNoCheckout(false)
                .call()) {

            // Build the metadata model to save inside our PostgreSQL system
            GitRepository metadataRecord = GitRepository.builder()
                    .name(repoName)
                    .url(repositoryUrl)
                    .localPath(targetLocalStoragePath.toAbsolutePath().toString())
                    .createdAt(LocalDateTime.now())
                    .build();

            return repositoryDataAccessor.save(metadataRecord);

        } catch (GitAPIException gitException) {
            // Ensure we clean up the abandoned directory if the network clone process fails
            deleteDirectoryFilesystemPayload(targetLocalStoragePath.toFile());
            throw new RuntimeException("Failed to execute Git operation: Check if the repository URL is correct and public", gitException);
        }
    }

    private String extractRepositoryName(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Repository target URL cannot be empty or null");
        }
        // Cleans up trailing slashes or .git suffix safely
        String sanitizedUrl = url.replaceAll("/+$", "").replaceAll("\\.git$", "");
        int lastSlashIndex = sanitizedUrl.lastIndexOf('/');
        if (lastSlashIndex == -1) {
            return "unknown-repository";
        }
        return sanitizedUrl.substring(lastSlashIndex + 1);
    }

    private void deleteDirectoryFilesystemPayload(File folder) {
        File[] containedFiles = folder.listFiles();
        if (containedFiles != null) {
            for (File file : containedFiles) {
                deleteDirectoryFilesystemPayload(file);
            }
        }
        folder.delete();
    }
}