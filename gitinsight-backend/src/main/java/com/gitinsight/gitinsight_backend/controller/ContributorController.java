package com.gitinsight.gitinsight_backend.controller;

import com.gitinsight.gitinsight_backend.dto.ContributorResponse;
import com.gitinsight.gitinsight_backend.repository.CommitRepository;
import com.gitinsight.gitinsight_backend.repository.ContributorRepository;
import com.gitinsight.gitinsight_backend.repository.GitRepositoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api") // FIX: Simplified this so both methods route correctly
public class ContributorController {

    private final ContributorRepository contributorRepository;
    private final GitRepositoryRepository gitRepositoryRepository;
    private final CommitRepository commitRepository; // 1. ADDED THIS MISSING DEPENDENCY

    // 2. UPDATED CONSTRUCTOR TO INJECT ALL 3 REPOSITORIES
    public ContributorController(ContributorRepository contributorRepository,
                                 GitRepositoryRepository gitRepositoryRepository,
                                 CommitRepository commitRepository) {
        this.contributorRepository = contributorRepository;
        this.gitRepositoryRepository = gitRepositoryRepository;
        this.commitRepository = commitRepository;
    }

    // Accessible at: GET http://localhost:8080/api/contributors?url=...
    @GetMapping("/contributors")
    public List<CommitRepository.ContributorProjection> getTopContributors(@RequestParam String url) {
        String decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8);
        return commitRepository.findTopContributorsByRepoUrl(decodedUrl); // Error is now gone!
    }
    // --- DATABASE X-RAY ENDPOINT ---
    @GetMapping("/debug/repos")
    public List<String> seeAllRepositoriesInDatabase() {
        return gitRepositoryRepository.findAll().stream()
                .map(repo -> "ID: " + repo.getId() + " | Name: " + repo.getName() + " | URL stored in DB: " + repo.getUrl())
                .collect(Collectors.toList());
    }

    // Accessible at: GET http://localhost:8080/api/repositories/{repositoryId}/contributors
    @GetMapping("/repositories/{repositoryId}/contributors")
    public ResponseEntity<List<ContributorResponse>> getContributorsForRepository(@PathVariable Long repositoryId) {
        return gitRepositoryRepository.findById(repositoryId)
                .map(repository -> {
                    List<ContributorResponse> contributors = contributorRepository.findByRepository(repository)
                            .stream()
                            .map(contributor -> ContributorResponse.builder()
                                    .name(contributor.getName())
                                    .commitCount(contributor.getCommitCount())
                                    .build())
                            .collect(Collectors.toList());

                    return ResponseEntity.ok(contributors);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}