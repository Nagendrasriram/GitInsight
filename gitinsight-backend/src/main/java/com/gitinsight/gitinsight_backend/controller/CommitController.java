package com.gitinsight.gitinsight_backend.controller;

import com.gitinsight.gitinsight_backend.dto.CommitResponse;
import com.gitinsight.gitinsight_backend.repository.CommitRepository;
import com.gitinsight.gitinsight_backend.repository.GitRepositoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/repositories/{repositoryId}/commits")
@CrossOrigin(origins = "*")
public class CommitController {

    private final CommitRepository commitRepository;
    private final GitRepositoryRepository gitRepositoryRepository;

    public CommitController(CommitRepository commitRepository, GitRepositoryRepository gitRepositoryRepository) {
        this.commitRepository = commitRepository;
        this.gitRepositoryRepository = gitRepositoryRepository;
    }

    @GetMapping
    public ResponseEntity<List<CommitResponse>> getCommitsForRepository(@PathVariable Long repositoryId) {
        // 1. Look up the repository. If it doesn't exist, return a 404 Error.
        return gitRepositoryRepository.findById(repositoryId)
                .map(repository -> {
                    // 2. Fetch the raw entities and map them to DTOs
                    List<CommitResponse> commits = commitRepository.findByRepository(repository)
                            .stream()
                            .map(commit -> CommitResponse.builder()
                                    .hash(commit.getHash())
                                    .author(commit.getAuthor())
                                    .message(commit.getMessage())
                                    .commitDate(commit.getCommitDate())
                                    .build())
                            .collect(Collectors.toList());

                    // 3. Return a 200 OK with the JSON data
                    return ResponseEntity.ok(commits);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}