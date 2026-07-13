package com.gitinsight.gitinsight_backend.controller;

import com.gitinsight.gitinsight_backend.dto.OwnershipResponse;
import com.gitinsight.gitinsight_backend.entity.GitRepository;
import com.gitinsight.gitinsight_backend.repository.GitRepositoryRepository;
import com.gitinsight.gitinsight_backend.repository.OwnershipRepository;
import com.gitinsight.gitinsight_backend.service.OwnershipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class OwnershipController {

    private static final Logger log = LoggerFactory.getLogger(OwnershipController.class);

    private final OwnershipRepository ownershipRepository;
    private final GitRepositoryRepository gitRepositoryRepository;
    private final OwnershipService ownershipService;

    public OwnershipController(OwnershipRepository ownershipRepository,
                               GitRepositoryRepository gitRepositoryRepository,
                               OwnershipService ownershipService) {
        this.ownershipRepository = ownershipRepository;
        this.gitRepositoryRepository = gitRepositoryRepository;
        this.ownershipService = ownershipService;
    }

    // =========================================================================
    // 1. DATABASE-BACKED OWNERSHIP — returns stored ownership by repository ID
    // =========================================================================
    @GetMapping("/repositories/{repositoryId}/ownership")
    public ResponseEntity<List<OwnershipResponse>> getOwnershipForRepository(
            @PathVariable Long repositoryId) {

        log.info("Fetching stored ownership for repositoryId={}", repositoryId);

        return gitRepositoryRepository.findById(repositoryId)
                .map(repository -> {
                    List<OwnershipResponse> ownershipData = ownershipRepository
                            .findByRepository(repository)
                            .stream()
                            .map(ownership -> OwnershipResponse.builder()
                                    .filePath(ownership.getFilePath())
                                    .contributorName(ownership.getContributorName())
                                    .changeCount(ownership.getChangeCount())
                                    .build())
                            .collect(Collectors.toList());

                    log.info("Found {} ownership records for repositoryId={}", ownershipData.size(), repositoryId);
                    return ResponseEntity.ok(ownershipData);
                })
                .orElseGet(() -> {
                    log.warn("Repository not found for repositoryId={}", repositoryId);
                    return ResponseEntity.notFound().build();
                });
    }

    // =========================================================================
    // 2. DYNAMIC FILE ANALYSIS — live JGit blame with fuzzy URL matching
    // =========================================================================
    @GetMapping("/ownership")
    public Map<String, Integer> getFileOwnership(
            @RequestParam String url,
            @RequestParam String file) {

        String decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8);

        // Normalize: lowercase, strip trailing slash, strip .git suffix
        String normalizedRequest = normalize(decodedUrl);

        log.info("=== OWNERSHIP LOOKUP START ===");
        log.info("Raw URL param    : {}", url);
        log.info("Decoded URL      : {}", decodedUrl);
        log.info("Normalized search: {}", normalizedRequest);
        log.info("File requested   : {}", file);

        List<GitRepository> allRepos = gitRepositoryRepository.findAll();

        log.info("Total repos in DB: {}", allRepos.size());
        allRepos.forEach(r ->
                log.info("  DB entry → id={} | url={} | normalized={} | localPath={}",
                        r.getId(), r.getUrl(), normalize(r.getUrl()), r.getLocalPath())
        );

        GitRepository repo = allRepos.stream()
                .filter(r -> {
                    String normalizedDb = normalize(r.getUrl());
                    boolean match = normalizedDb.contains(normalizedRequest)
                            || normalizedRequest.contains(normalizedDb);
                    log.debug("  Comparing '{}' vs '{}' → match={}", normalizedRequest, normalizedDb, match);
                    return match;
                })
                .findFirst()
                .orElseThrow(() -> {
                    log.error("No matching repository found for normalized URL: {}", normalizedRequest);
                    return new RuntimeException(
                            "Repository not found in DB. " +
                                    "Searched for: [" + normalizedRequest + "]. " +
                                    "Available repos: " + allRepos.stream()
                                    .map(r -> normalize(r.getUrl()))
                                    .collect(Collectors.joining(", "))
                    );
                });

        log.info("Matched repo → id={} | url={} | localPath={}", repo.getId(), repo.getUrl(), repo.getLocalPath());
        log.info("=== OWNERSHIP LOOKUP END ===");

        return ownershipService.analyzeFileOwnership(repo.getLocalPath(), file);
    }

    // =========================================================================
    // HELPER — normalize URL for fuzzy comparison
    // =========================================================================
    private String normalize(String url) {
        if (url == null) return "";
        return url.trim()
                .toLowerCase()
                .replaceAll("/$", "")       // remove trailing slash
                .replace(".git", "")         // remove .git suffix
                .replace("https://", "")     // strip protocol for pure path matching
                .replace("http://", "");
    }
}
//package com.gitinsight.gitinsight_backend.controller;
//
//import com.gitinsight.gitinsight_backend.dto.OwnershipResponse;
//import com.gitinsight.gitinsight_backend.repository.GitRepositoryRepository;
//import com.gitinsight.gitinsight_backend.repository.OwnershipRepository;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//import java.util.stream.Collectors;
//
//@RestController
//@RequestMapping("/api/repositories/{repositoryId}/ownership")
//@CrossOrigin(origins = "*")
//public class OwnershipController {
//
//    private final OwnershipRepository ownershipRepository;
//    private final GitRepositoryRepository gitRepositoryRepository;
//
//    public OwnershipController(OwnershipRepository ownershipRepository, GitRepositoryRepository gitRepositoryRepository) {
//        this.ownershipRepository = ownershipRepository;
//        this.gitRepositoryRepository = gitRepositoryRepository;
//    }
//
//    @GetMapping
//    public ResponseEntity<List<OwnershipResponse>> getOwnershipForRepository(@PathVariable Long repositoryId) {
//        return gitRepositoryRepository.findById(repositoryId)
//                .map(repository -> {
//                    List<OwnershipResponse> ownershipData = ownershipRepository.findByRepository(repository)
//                            .stream()
//                            .map(ownership -> OwnershipResponse.builder()
//                                    .filePath(ownership.getFilePath())
//                                    .contributorName(ownership.getContributorName())
//                                    .changeCount(ownership.getChangeCount())
//                                    .build())
//                            .collect(Collectors.toList());
//
//                    return ResponseEntity.ok(ownershipData);
//                })
//                .orElse(ResponseEntity.notFound().build());
//    }
//}