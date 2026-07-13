package com.gitinsight.gitinsight_backend.controller;

import com.gitinsight.gitinsight_backend.dto.DashboardResponse;
import com.gitinsight.gitinsight_backend.dto.RepositorySummaryResponse;
import com.gitinsight.gitinsight_backend.entity.GitRepository;
import com.gitinsight.gitinsight_backend.repository.GitRepositoryRepository;
import com.gitinsight.gitinsight_backend.service.CommitExtractionService;
import com.gitinsight.gitinsight_backend.services.*;
import com.gitinsight.gitinsight_backend.service.DependencyExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryController {

    private static final Logger log = LoggerFactory.getLogger(RepositoryController.class);

    private final GitRepositoryRepository repositoryDataAccessor;
    private final VectorIndexingService vectorIndexingService;
    private final GitService gitService;
    private final ProjectInsightService projectInsightService;
    private final CodeReviewService codeReviewService;
    private final CommitExtractionService commitExtractionService;
    private final BusFactorService busFactorService;
    private final DependencyExtractionService dependencyExtractionService; // <-- NEW

    public RepositoryController(GitRepositoryRepository repositoryDataAccessor,
                                VectorIndexingService vectorIndexingService,
                                GitService gitService,
                                ProjectInsightService projectInsightService,
                                CodeReviewService codeReviewService,
                                CommitExtractionService commitExtractionService,
                                DependencyExtractionService dependencyExtractionService,
                                BusFactorService busFactorService) { // <-- NEW
        this.repositoryDataAccessor = repositoryDataAccessor;
        this.vectorIndexingService = vectorIndexingService;
        this.gitService = gitService;
        this.projectInsightService = projectInsightService;
        this.codeReviewService = codeReviewService;
        this.commitExtractionService = commitExtractionService;
        this.dependencyExtractionService = dependencyExtractionService;
        this.busFactorService = busFactorService;// <-- NEW
    }


    // =========================================================================
    // 1. GET ALL REPOSITORIES
    // GET /api/repositories
    // =========================================================================
    @GetMapping
    public List<RepositorySummaryResponse> getAllRepositories() {
        log.info("Fetching all analyzed repositories");

        List<RepositorySummaryResponse> repos = repositoryDataAccessor.findAll().stream()
                .map(entity -> RepositorySummaryResponse.builder()
                        .id(entity.getId())
                        .name(entity.getName())
                        .url(entity.getUrl())
                        .analyzedAt(entity.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        log.info("Returning {} repositories", repos.size());
        return repos;
    }

    // =========================================================================
    // 2. ANALYZE REPOSITORY — clone → index → upsert → extract commits → extract deps
    // GET /api/repositories/analyze?url=...
    // =========================================================================
    @GetMapping("/analyze")
    public ResponseEntity<Map<String, String>> analyzeRepository(@RequestParam String url) {
        log.info("=== ANALYSIS REQUEST START ===");
        log.info("Repository URL: {}", url);

        // Sanitize URL — strip /tree/ branches, ensure .git suffix
        String cleanUrl = url;
        if (cleanUrl.contains("/tree/")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.indexOf("/tree/"));
        }
        if (!cleanUrl.endsWith(".git")) {
            cleanUrl = cleanUrl + ".git";
        }
        log.info("Sanitized URL: {}", cleanUrl);

        final String finalUrl = cleanUrl;

        try {
            // ── Step 1: Clone ─────────────────────────────────────────────────
            log.info("Step 1: Cloning repository...");
            String localPath = gitService.cloneRepository(finalUrl);
            log.info("Step 1: Clone successful → localPath={}", localPath);

            // ── Step 2: Index into vector store ───────────────────────────────
            log.info("Step 2: Indexing repository into vector store...");
            vectorIndexingService.indexRepository(localPath, finalUrl);
            log.info("Step 2: Indexing complete for url={}", finalUrl);

            // ── Step 3: Upsert repository record in DB ────────────────────────
            log.info("Step 3: Upserting repository record in DB...");
            GitRepository savedRepo = repositoryDataAccessor.findByUrl(finalUrl)
                    .map(existingRepo -> {
                        log.info("Step 3: Repo already in DB (id={}). Updating localPath.", existingRepo.getId());
                        existingRepo.setLocalPath(localPath);
                        return repositoryDataAccessor.save(existingRepo);
                    })
                    .orElseGet(() -> {
                        String repoName = finalUrl
                                .substring(finalUrl.lastIndexOf("/") + 1)
                                .replace(".git", "");
                        GitRepository newRepo = GitRepository.builder()
                                .url(finalUrl)
                                .name(repoName)
                                .localPath(localPath)
                                .createdAt(LocalDateTime.now())
                                .build();
                        GitRepository saved = repositoryDataAccessor.save(newRepo);
                        log.info("Step 3: New repo saved → name={} | id={}", repoName, saved.getId());
                        return saved;
                    });

            // ── Step 4: Extract commit history ────────────────────────────────
            log.info("Step 4: Extracting commit history...");
            commitExtractionService.extractAndSaveCommits(savedRepo);
            log.info("Step 4: Commit extraction complete.");

            // ── Step 5: Extract knowledge graph dependencies ──────────────────
            log.info("Step 5: Extracting knowledge graph dependencies...");
            dependencyExtractionService.extractAndSaveDependencies(savedRepo);
            log.info("Step 5: Dependency extraction complete.");

            log.info("=== ANALYSIS REQUEST END — SUCCESS ===");
            return ResponseEntity.ok(
                    Map.of("message", "Full analysis complete! Commits indexed, dependencies mapped, and knowledge graph built.")
            );

        } catch (Exception e) {
            log.error("=== ANALYSIS REQUEST END — FAILED ===");
            log.error("Failed to analyze url={} | reason={}", finalUrl, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // =========================================================================
    // 3. AI DASHBOARD SUMMARY
    // GET /api/repositories/summary?url=...
    // =========================================================================
    @GetMapping("/summary")
    public DashboardResponse getSummaryByUrl(@RequestParam String url) {
        log.info("AI summary requested for url={}", url);

        // 1. Get the base summary data
        DashboardResponse baseResponse = projectInsightService.generateRepositorySummaryByUrl(url);

        // 2. Calculate the bus factor
        int busFactor = busFactorService.calculateBusFactor(url);

        // 3. Return a NEW instance of the record with all data included
        return new DashboardResponse(
                baseResponse.repositoryName(),
                baseResponse.aiSummary(),
                baseResponse.totalCommits(),
                baseResponse.recentCommits(),
                baseResponse.dailyCommitCounts(),
                baseResponse.healthScore(),
                busFactor // The new value!
        );
    }

    // =========================================================================
    // 4. AI CODE REVIEW
    // GET /api/repositories/code-review?url=...
    // =========================================================================
    @GetMapping("/code-review")
    public String getDeepCodeReview(@RequestParam String url) {
        log.info("Deep code review requested for url={}", url);
        return codeReviewService.performDeepCodeReview(url);
    }

    // =========================================================================
    // 5. ARCHITECTURE DIAGRAM — generates Mermaid.js diagram from file structure
    // GET /api/repositories/architecture?url=...
    // =========================================================================
    @GetMapping("/architecture")
    public ResponseEntity<Map<String, String>> getArchitectureDiagram(@RequestParam String url) {
        log.info("Architecture diagram requested for url={}", url);

        // Sanitize URL — same as /analyze
        String cleanUrl = url;
        if (cleanUrl.contains("/tree/")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.indexOf("/tree/"));
        }
        if (!cleanUrl.endsWith(".git")) {
            cleanUrl = cleanUrl + ".git";
        }
        log.info("Sanitized URL for architecture: {}", cleanUrl);

        try {
            String mermaidScript = projectInsightService.generateArchitectureDiagram(cleanUrl);
            log.info("Architecture diagram generated successfully for url={}", cleanUrl);
            return ResponseEntity.ok(Map.of("diagram", mermaidScript));
        } catch (Exception e) {
            log.error("Failed to generate architecture diagram for url={} | reason={}", cleanUrl, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}





//package com.gitinsight.gitinsight_backend.controller;
//
//import com.gitinsight.gitinsight_backend.dto.DashboardResponse;
//import com.gitinsight.gitinsight_backend.dto.RepositorySummaryResponse;
//import com.gitinsight.gitinsight_backend.entity.GitRepository;
//import com.gitinsight.gitinsight_backend.repository.GitRepositoryRepository;
//import com.gitinsight.gitinsight_backend.service.CommitExtractionService;
//import com.gitinsight.gitinsight_backend.services.CodeReviewService;
//import com.gitinsight.gitinsight_backend.services.GitService;
//import com.gitinsight.gitinsight_backend.services.ProjectInsightService;
//import com.gitinsight.gitinsight_backend.services.VectorIndexingService;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//@RestController
//@RequestMapping("/api/repositories")
//public class RepositoryController {
//
//    private static final Logger log = LoggerFactory.getLogger(RepositoryController.class);
//
//    private final GitRepositoryRepository repositoryDataAccessor;
//    private final VectorIndexingService vectorIndexingService;
//    private final GitService gitService;
//    private final ProjectInsightService projectInsightService;
//    private final CodeReviewService codeReviewService;
//    private final CommitExtractionService commitExtractionService;
//
//    public RepositoryController(GitRepositoryRepository repositoryDataAccessor,
//                                VectorIndexingService vectorIndexingService,
//                                GitService gitService,
//                                ProjectInsightService projectInsightService,
//                                CodeReviewService codeReviewService,
//                                CommitExtractionService commitExtractionService) {
//        this.repositoryDataAccessor = repositoryDataAccessor;
//        this.vectorIndexingService = vectorIndexingService;
//        this.gitService = gitService;
//        this.projectInsightService = projectInsightService;
//        this.codeReviewService = codeReviewService;
//        this.commitExtractionService = commitExtractionService;
//    }
//
//    // =========================================================================
//    // 1. GET ALL REPOSITORIES
//    // GET /api/repositories
//    // =========================================================================
//    @GetMapping
//    public List<RepositorySummaryResponse> getAllRepositories() {
//        log.info("Fetching all analyzed repositories");
//
//        List<RepositorySummaryResponse> repos = repositoryDataAccessor.findAll().stream()
//                .map(entity -> RepositorySummaryResponse.builder()
//                        .id(entity.getId())
//                        .name(entity.getName())
//                        .url(entity.getUrl())
//                        .analyzedAt(entity.getCreatedAt())
//                        .build())
//                .collect(Collectors.toList());
//
//        log.info("Returning {} repositories", repos.size());
//        return repos;
//    }
//
//    // =========================================================================
//    // 2. ANALYZE REPOSITORY — clone → index → upsert → extract commits
//    // GET /api/repositories/analyze?url=...
//    // =========================================================================
//    @GetMapping("/analyze")
//    public ResponseEntity<Map<String, String>> analyzeRepository(@RequestParam String url) {
//        log.info("=== ANALYSIS REQUEST START ===");
//        log.info("Repository URL: {}", url);
//
//        // Sanitize URL — strip /tree/ branches, ensure .git suffix
//        String cleanUrl = url;
//        if (cleanUrl.contains("/tree/")) {
//            cleanUrl = cleanUrl.substring(0, cleanUrl.indexOf("/tree/"));
//        }
//        if (!cleanUrl.endsWith(".git")) {
//            cleanUrl = cleanUrl + ".git";
//        }
//        log.info("Sanitized URL: {}", cleanUrl);
//
//        final String finalUrl = cleanUrl;
//
//        try {
//            // ── Step 1: Clone ─────────────────────────────────────────────────
//            log.info("Step 1: Cloning repository...");
//            String localPath = gitService.cloneRepository(finalUrl);
//            log.info("Step 1: Clone successful → localPath={}", localPath);
//
//            // ── Step 2: Index into vector store ───────────────────────────────
//            log.info("Step 2: Indexing repository into vector store...");
//            vectorIndexingService.indexRepository(localPath, finalUrl);
//            log.info("Step 2: Indexing complete for url={}", finalUrl);
//
//            // ── Step 3: Upsert repository record in DB ────────────────────────
//            log.info("Step 3: Upserting repository record in DB...");
//            GitRepository savedRepo = repositoryDataAccessor.findByUrl(finalUrl)
//                    .map(existingRepo -> {
//                        log.info("Step 3: Repo already in DB (id={}). Updating localPath.", existingRepo.getId());
//                        existingRepo.setLocalPath(localPath);
//                        return repositoryDataAccessor.save(existingRepo);
//                    })
//                    .orElseGet(() -> {
//                        String repoName = finalUrl
//                                .substring(finalUrl.lastIndexOf("/") + 1)
//                                .replace(".git", "");
//                        GitRepository newRepo = GitRepository.builder()
//                                .url(finalUrl)
//                                .name(repoName)
//                                .localPath(localPath)
//                                .createdAt(LocalDateTime.now())
//                                .build();
//                        GitRepository saved = repositoryDataAccessor.save(newRepo);
//                        log.info("Step 3: New repo saved → name={} | id={}", repoName, saved.getId());
//                        return saved;
//                    });
//
//            // ── Step 4: Extract commit history ────────────────────────────────
//            log.info("Step 4: Extracting commit history...");
//            commitExtractionService.extractAndSaveCommits(savedRepo);
//            log.info("Step 4: Commit extraction complete.");
//
//            log.info("=== ANALYSIS REQUEST END — SUCCESS ===");
//            return ResponseEntity.ok(
//                    Map.of("message", "Full analysis complete! Commits indexed and saved.")
//            );
//
//        } catch (Exception e) {
//            log.error("=== ANALYSIS REQUEST END — FAILED ===");
//            log.error("Failed to analyze url={} | reason={}", finalUrl, e.getMessage(), e);
//            return ResponseEntity.internalServerError()
//                    .body(Map.of("error", e.getMessage()));
//        }
//    }
//
//    // =========================================================================
//    // 3. AI DASHBOARD SUMMARY
//    // GET /api/repositories/summary?url=...
//    // =========================================================================
//    @GetMapping("/summary")
//    public DashboardResponse getSummaryByUrl(@RequestParam String url) {
//        log.info("AI summary requested for url={}", url);
//        return projectInsightService.generateRepositorySummaryByUrl(url);
//    }
//
//    // =========================================================================
//    // 4. AI CODE REVIEW
//    // GET /api/repositories/code-review?url=...
//    // =========================================================================
//    @GetMapping("/code-review")
//    public String getDeepCodeReview(@RequestParam String url) {
//        log.info("Deep code review requested for url={}", url);
//        return codeReviewService.performDeepCodeReview(url);
//    }
//
//    // =========================================================================
//    // 5. ARCHITECTURE DIAGRAM — generates Mermaid.js diagram from file structure
//    // GET /api/repositories/architecture?url=...
//    // =========================================================================
//    @GetMapping("/architecture")
//    public ResponseEntity<Map<String, String>> getArchitectureDiagram(@RequestParam String url) {
//        log.info("Architecture diagram requested for url={}", url);
//
//        // Sanitize URL — same as /analyze
//        String cleanUrl = url;
//        if (cleanUrl.contains("/tree/")) {
//            cleanUrl = cleanUrl.substring(0, cleanUrl.indexOf("/tree/"));
//        }
//        if (!cleanUrl.endsWith(".git")) {
//            cleanUrl = cleanUrl + ".git";
//        }
//        log.info("Sanitized URL for architecture: {}", cleanUrl);
//
//        try {
//            String mermaidScript = projectInsightService.generateArchitectureDiagram(cleanUrl);
//            log.info("Architecture diagram generated successfully for url={}", cleanUrl);
//            return ResponseEntity.ok(Map.of("diagram", mermaidScript));
//        } catch (Exception e) {
//            log.error("Failed to generate architecture diagram for url={} | reason={}", cleanUrl, e.getMessage(), e);
//            return ResponseEntity.internalServerError()
//                    .body(Map.of("error", e.getMessage()));
//        }
//    }
//}






//package com.gitinsight.gitinsight_backend.controller;
//
//import com.gitinsight.gitinsight_backend.dto.DashboardResponse;
//import com.gitinsight.gitinsight_backend.dto.RepositorySummaryResponse;
//import com.gitinsight.gitinsight_backend.entity.GitRepository;
//import com.gitinsight.gitinsight_backend.repository.GitRepositoryRepository;
//import com.gitinsight.gitinsight_backend.service.CommitExtractionService;
//import com.gitinsight.gitinsight_backend.services.CodeReviewService;
//import com.gitinsight.gitinsight_backend.services.GitService;
//import com.gitinsight.gitinsight_backend.services.ProjectInsightService;
//import com.gitinsight.gitinsight_backend.services.VectorIndexingService;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//@RestController
//@RequestMapping("/api/repositories")
//public class RepositoryController {
//
//    private static final Logger log = LoggerFactory.getLogger(RepositoryController.class);
//
//    private final GitRepositoryRepository repositoryDataAccessor;
//    private final VectorIndexingService vectorIndexingService;
//    private final GitService gitService;
//    private final ProjectInsightService projectInsightService;
//    private final CodeReviewService codeReviewService;
//    private final CommitExtractionService commitExtractionService;
//
//    public RepositoryController(GitRepositoryRepository repositoryDataAccessor,
//                                VectorIndexingService vectorIndexingService,
//                                GitService gitService,
//                                ProjectInsightService projectInsightService,
//                                CodeReviewService codeReviewService,
//                                CommitExtractionService commitExtractionService) {
//        this.repositoryDataAccessor = repositoryDataAccessor;
//        this.vectorIndexingService = vectorIndexingService;
//        this.gitService = gitService;
//        this.projectInsightService = projectInsightService;
//        this.codeReviewService = codeReviewService;
//        this.commitExtractionService = commitExtractionService;
//    }
//
//    // =========================================================================
//    // 1. GET ALL REPOSITORIES
//    // GET /api/repositories
//    // =========================================================================
//    @GetMapping
//    public List<RepositorySummaryResponse> getAllRepositories() {
//        log.info("Fetching all analyzed repositories");
//
//        List<RepositorySummaryResponse> repos = repositoryDataAccessor.findAll().stream()
//                .map(entity -> RepositorySummaryResponse.builder()
//                        .id(entity.getId())
//                        .name(entity.getName())
//                        .url(entity.getUrl())
//                        .analyzedAt(entity.getCreatedAt())
//                        .build())
//                .collect(Collectors.toList());
//
//        log.info("Returning {} repositories", repos.size());
//        return repos;
//    }
//
//    // =========================================================================
//    // 2. ANALYZE REPOSITORY — clone → index → save → extract commits
//    // GET /api/repositories/analyze?url=...
//    // =========================================================================
//    @GetMapping("/analyze")
//    public ResponseEntity<Map<String, String>> analyzeRepository(@RequestParam String url) {
//
//        log.info("=== ANALYSIS REQUEST START ===");
//        log.info("Repository URL: {}", url);
//        String cleanUrl = url;
//        if (cleanUrl.contains("/tree/")) {
//            cleanUrl = cleanUrl.substring(0, cleanUrl.indexOf("/tree/"));
//        }
//        if (!cleanUrl.endsWith(".git")) {
//            cleanUrl = cleanUrl + ".git";
//        }
//        log.info("Sanitized URL: {}", cleanUrl);
//        try {
//            // ── Step 1: Clone ─────────────────────────────────────────────────
//            log.info("Step 1: Cloning repository...");
//            String localPath = gitService.cloneRepository(url);
//            log.info("Step 1: Clone successful → localPath={}", localPath);
//
//            // ── Step 2: Index into vector store ───────────────────────────────
//            log.info("Step 2: Indexing repository into vector store...");
//            vectorIndexingService.indexRepository(localPath, url);
//            log.info("Step 2: Indexing complete for url={}", url);
//
//            // ── Step 3: Upsert repository record in DB ────────────────────────
//            // If repo already exists → update localPath
//            // If repo is new        → insert fresh record
//            log.info("Step 3: Upserting repository record in DB...");
//            GitRepository savedRepo = repositoryDataAccessor.findByUrl(url)
//                    .map(existingRepo -> {
//                        log.info("Step 3: Repo already in DB (id={}). Updating localPath.", existingRepo.getId());
//                        existingRepo.setLocalPath(localPath);
//                        return repositoryDataAccessor.save(existingRepo);
//                    })
//                    .orElseGet(() -> {
//                        String repoName = url.substring(url.lastIndexOf("/") + 1)
//                                .replace(".git", "");
//                        GitRepository newRepo = GitRepository.builder()
//                                .url(url)
//                                .name(repoName)
//                                .localPath(localPath)
//                                .createdAt(LocalDateTime.now())
//                                .build();
//                        GitRepository saved = repositoryDataAccessor.save(newRepo);
//                        log.info("Step 3: New repo saved to DB → name={} | id={}", repoName, saved.getId());
//                        return saved;
//                    });
//
//            // ── Step 4: Extract commit history into SQL DB ────────────────────
//            // This populates the commits table so the dashboard charts work
//            log.info("Step 4: Extracting commit history...");
//            commitExtractionService.extractAndSaveCommits(savedRepo);
//            log.info("Step 4: Commit extraction complete.");
//
//            log.info("=== ANALYSIS REQUEST END — SUCCESS ===");
//            return ResponseEntity.ok(
//                    Map.of("message", "Full analysis complete! Commits indexed and saved.")
//            );
//
//        } catch (Exception e) {
//            log.error("=== ANALYSIS REQUEST END — FAILED ===");
//            log.error("Failed to analyze url={} | reason={}", url, e.getMessage(), e);
//            return ResponseEntity.internalServerError()
//                    .body(Map.of("error", e.getMessage()));
//        }
//
//    }
//
//    // =========================================================================
//    // 3. AI DASHBOARD SUMMARY
//    // GET /api/repositories/summary?url=...
//    // =========================================================================
//    @GetMapping("/summary")
//    public DashboardResponse getSummaryByUrl(@RequestParam String url) {
//        log.info("AI summary requested for url={}", url);
//        return projectInsightService.generateRepositorySummaryByUrl(url);
//    }
//
//    // =========================================================================
//    // 4. AI CODE REVIEW
//    // GET /api/repositories/code-review?url=...
//    // =========================================================================
//    @GetMapping("/code-review")
//    public String getDeepCodeReview(@RequestParam String url) {
//        log.info("Deep code review requested for url={}", url);
//        return codeReviewService.performDeepCodeReview(url);
//    }
//}
//package com.gitinsight.gitinsight_backend.controller;
//
//import com.gitinsight.gitinsight_backend.dto.DashboardResponse;
//import com.gitinsight.gitinsight_backend.dto.RepositorySummaryResponse;
//import com.gitinsight.gitinsight_backend.entity.GitRepository;
//import com.gitinsight.gitinsight_backend.repository.GitRepositoryRepository;
//import com.gitinsight.gitinsight_backend.services.CodeReviewService;
//import com.gitinsight.gitinsight_backend.services.GitService;
//import com.gitinsight.gitinsight_backend.services.ProjectInsightService;
//import com.gitinsight.gitinsight_backend.services.VectorIndexingService;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//@RestController
//@RequestMapping("/api/repositories")
//public class RepositoryController {
//
//    private static final Logger log = LoggerFactory.getLogger(RepositoryController.class);
//
//    private final GitRepositoryRepository repositoryDataAccessor;
//    private final VectorIndexingService vectorIndexingService;
//    private final GitService gitService;
//    private final ProjectInsightService projectInsightService;
//    private final CodeReviewService codeReviewService;
//
//    public RepositoryController(GitRepositoryRepository repositoryDataAccessor,
//                                VectorIndexingService vectorIndexingService,
//                                GitService gitService,
//                                ProjectInsightService projectInsightService,
//                                CodeReviewService codeReviewService) {
//        this.repositoryDataAccessor = repositoryDataAccessor;
//        this.vectorIndexingService = vectorIndexingService;
//        this.gitService = gitService;
//        this.projectInsightService = projectInsightService;
//        this.codeReviewService = codeReviewService;
//    }
//
//    // =========================================================================
//    // 1. GET ALL REPOSITORIES
//    // GET /api/repositories
//    // =========================================================================
//    @GetMapping
//    public List<RepositorySummaryResponse> getAllRepositories() {
//        log.info("Fetching all analyzed repositories");
//
//        List<RepositorySummaryResponse> repos = repositoryDataAccessor.findAll().stream()
//                .map(entity -> RepositorySummaryResponse.builder()
//                        .id(entity.getId())
//                        .name(entity.getName())
//                        .url(entity.getUrl())
//                        .analyzedAt(entity.getCreatedAt())
//                        .build())
//                .collect(Collectors.toList());
//
//        log.info("Returning {} repositories", repos.size());
//        return repos;
//    }
//
//    // =========================================================================
//    // 2. ANALYZE REPOSITORY — clone, index, save (skip save if already exists)
//    // GET /api/repositories/analyze?url=...
//    // =========================================================================
//    @GetMapping("/analyze")
//    public ResponseEntity<Map<String, String>> analyzeRepository(@RequestParam String url) {
//        log.info("=== ANALYSIS REQUEST START ===");
//        log.info("Repository URL: {}", url);
//
//        try {
//            // Step 1: Clone the repository to local disk via JGit
//            log.info("Step 1: Cloning repository...");
//            String localPath = gitService.cloneRepository(url);
//            log.info("Step 1: Clone successful → localPath={}", localPath);
//
//            // Step 2: Chunk all files and store embeddings in vector store
//            log.info("Step 2: Indexing repository into vector store...");
//            vectorIndexingService.indexRepository(localPath, url);
//            log.info("Step 2: Indexing complete for url={}", url);
//
//            // Step 3: Save to DB only if this URL doesn't already exist
//            // This prevents: duplicate key violates unique constraint "repositories_url_key"
//            log.info("Step 3: Checking if repository already exists in DB...");
//            repositoryDataAccessor.findByUrl(url).ifPresentOrElse(
//                    existingRepo -> {
//                        // Already in DB — update the localPath in case it changed
//                        log.info("Step 3: Repository already exists in DB (id={}). Updating localPath.", existingRepo.getId());
//                        existingRepo.setLocalPath(localPath);
//                        repositoryDataAccessor.save(existingRepo);
//                    },
//                    () -> {
//                        // Not in DB — insert fresh record
//                        String repoName = url.substring(url.lastIndexOf("/") + 1)
//                                .replace(".git", "");
//                        GitRepository newRepo = GitRepository.builder()
//                                .url(url)
//                                .name(repoName)
//                                .localPath(localPath)
//                                .createdAt(LocalDateTime.now())
//                                .build();
//                        repositoryDataAccessor.save(newRepo);
//                        log.info("Step 3: New repository saved to DB → name={}", repoName);
//                    }
//            );
//
//            log.info("=== ANALYSIS REQUEST END — SUCCESS ===");
//            return ResponseEntity.ok(
//                    Map.of("message", "Repository cloned, indexed, and saved successfully!")
//            );
//
//        } catch (Exception e) {
//            log.error("=== ANALYSIS REQUEST END — FAILED ===");
//            log.error("Failed to analyze url={} | reason={}", url, e.getMessage(), e);
//            return ResponseEntity.internalServerError()
//                    .body(Map.of("error", e.getMessage()));
//        }
//    }
//
//    // =========================================================================
//    // 3. AI DASHBOARD SUMMARY
//    // GET /api/repositories/summary?url=...
//    // =========================================================================
//    @GetMapping("/summary")
//    public DashboardResponse getSummaryByUrl(@RequestParam String url) {
//        log.info("AI summary requested for url={}", url);
//        return projectInsightService.generateRepositorySummaryByUrl(url);
//    }
//
//    // =========================================================================
//    // 4. AI CODE REVIEW
//    // GET /api/repositories/code-review?url=...
//    // =========================================================================
//    @GetMapping("/code-review")
//    public String getDeepCodeReview(@RequestParam String url) {
//        log.info("Deep code review requested for url={}", url);
//        return codeReviewService.performDeepCodeReview(url);
//    }
//}
//package com.gitinsight.gitinsight_backend.controller;
//
//import com.gitinsight.gitinsight_backend.dto.DashboardResponse;
//import com.gitinsight.gitinsight_backend.dto.RepositorySummaryResponse;
//import com.gitinsight.gitinsight_backend.entity.GitRepository;
//import com.gitinsight.gitinsight_backend.repository.GitRepositoryRepository;
//import com.gitinsight.gitinsight_backend.services.CodeReviewService;
//import com.gitinsight.gitinsight_backend.services.GitService;
//import com.gitinsight.gitinsight_backend.services.ProjectInsightService;
//import com.gitinsight.gitinsight_backend.services.VectorIndexingService;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//@RestController
//@RequestMapping("/api/repositories")
//public class RepositoryController {
//
//    private static final Logger log = LoggerFactory.getLogger(RepositoryController.class);
//
//    private final GitRepositoryRepository repositoryDataAccessor;
//    private final VectorIndexingService vectorIndexingService;
//    private final GitService gitService;
//    private final ProjectInsightService projectInsightService;
//    private final CodeReviewService codeReviewService;
//
//    public RepositoryController(GitRepositoryRepository repositoryDataAccessor,
//                                VectorIndexingService vectorIndexingService,
//                                GitService gitService,
//                                ProjectInsightService projectInsightService,
//                                CodeReviewService codeReviewService) {
//        this.repositoryDataAccessor = repositoryDataAccessor;
//        this.vectorIndexingService = vectorIndexingService;
//        this.gitService = gitService;
//        this.projectInsightService = projectInsightService;
//        this.codeReviewService = codeReviewService;
//    }
//
//    // =========================================================================
//    // 1. GET ALL REPOSITORIES — returns summary of all analyzed repos
//    // GET /api/repositories
//    // =========================================================================
//    @GetMapping
//    public List<RepositorySummaryResponse> getAllRepositories() {
//        log.info("Fetching all analyzed repositories");
//
//        List<RepositorySummaryResponse> repos = repositoryDataAccessor.findAll().stream()
//                .map(entity -> RepositorySummaryResponse.builder()
//                        .id(entity.getId())
//                        .name(entity.getName())
//                        .url(entity.getUrl())
//                        .analyzedAt(entity.getCreatedAt())
//                        .build())
//                .collect(Collectors.toList());
//
//        log.info("Returning {} repositories", repos.size());
//        return repos;
//    }
//
//    // =========================================================================
//    // 2. ANALYZE REPOSITORY — clone, index into vector store, save to DB
//    // GET /api/repositories/analyze?url=...
//    // Returns JSON so frontend doesn't crash with "Unexpected token" error
//    // =========================================================================
//    @GetMapping("/analyze")
//    public ResponseEntity<Map<String, String>> analyzeRepository(@RequestParam String url) {
//        log.info("=== ANALYSIS REQUEST START ===");
//        log.info("Repository URL: {}", url);
//
//        try {
//            // Step 1: Clone the repository to local disk via JGit
//            log.info("Step 1: Cloning repository...");
//            String localPath = gitService.cloneRepository(url);
//            log.info("Step 1: Clone successful → localPath={}", localPath);
//
//            // Step 2: Chunk all files and store embeddings in vector store
//            log.info("Step 2: Indexing repository into vector store...");
//            vectorIndexingService.indexRepository(localPath, url);
//            log.info("Step 2: Indexing complete for url={}", url);
//
//            // Step 3: Persist repository metadata to PostgreSQL
//            log.info("Step 3: Saving repository metadata to database...");
//            String repoName = url.substring(url.lastIndexOf("/") + 1)
//                    .replace(".git", "");
//
//            GitRepository newRepo = GitRepository.builder()
//                    .url(url)
//                    .name(repoName)
//                    .localPath(localPath)
//                    .createdAt(LocalDateTime.now())
//                    .build();
//
//            repositoryDataAccessor.save(newRepo);
//            log.info("Step 3: Repository saved to DB → name={}", repoName);
//
//            log.info("=== ANALYSIS REQUEST END — SUCCESS ===");
//            return ResponseEntity.ok(
//                    Map.of("message", "Repository cloned, indexed, and saved successfully!")
//            );
//
//        } catch (Exception e) {
//            log.error("=== ANALYSIS REQUEST END — FAILED ===");
//            log.error("Failed to analyze url={} | reason={}", url, e.getMessage(), e);
//            return ResponseEntity.internalServerError()
//                    .body(Map.of("error", e.getMessage()));
//        }
//    }
//
//    // =========================================================================
//    // 3. AI DASHBOARD SUMMARY — generates AI summary for the repo
//    // GET /api/repositories/summary?url=...
//    // =========================================================================
//    @GetMapping("/summary")
//    public DashboardResponse getSummaryByUrl(@RequestParam String url) {
//        log.info("AI summary requested for url={}", url);
//        return projectInsightService.generateRepositorySummaryByUrl(url);
//    }
//
//    // =========================================================================
//    // 4. AI CODE REVIEW — performs deep AI code review for the repo
//    // GET /api/repositories/code-review?url=...
//    // =========================================================================
//    @GetMapping("/code-review")
//    public String getDeepCodeReview(@RequestParam String url) {
//        log.info("Deep code review requested for url={}", url);
//        return codeReviewService.performDeepCodeReview(url);
//    }
//}
//package com.gitinsight.gitinsight_backend.controller;
//
//import com.gitinsight.gitinsight_backend.dto.RepositorySummaryResponse;
//import com.gitinsight.gitinsight_backend.entity.GitRepository;
//import com.gitinsight.gitinsight_backend.repository.GitRepositoryRepository;
//import com.gitinsight.gitinsight_backend.services.VectorIndexingService;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//import java.util.stream.Collectors;
//
//@RestController
//@RequestMapping("/api/repositories")
//public class RepositoryController {
//
//    private final GitRepositoryRepository repositoryDataAccessor;
//    private final VectorIndexingService vectorIndexingService;
//
//    // Constructor Injection
//    public RepositoryController(GitRepositoryRepository repositoryDataAccessor,
//                                VectorIndexingService vectorIndexingService) {
//        this.repositoryDataAccessor = repositoryDataAccessor;
//        this.vectorIndexingService = vectorIndexingService;
//    }
//
//    // 1. Existing Endpoint: List all repos
//    @GetMapping
//    public List<RepositorySummaryResponse> getAllRepositories() {
//        return repositoryDataAccessor.findAll().stream()
//                .map(entity -> RepositorySummaryResponse.builder()
//                        .id(entity.getId())
//                        .name(entity.getName())
//                        .url(entity.getUrl())
//                        .analyzedAt(entity.getCreatedAt())
//                        .build())
//                .collect(Collectors.toList());
//    }
//
//    // 2. NEW Endpoint: Trigger the analysis and indexing
//    // Triggered by: fetch(`${API}/analyze?url=${enc()}`)
//    @GetMapping("/analyze")
//    public ResponseEntity<String> analyzeRepository(@RequestParam String url) {
//        try {
//            System.out.println("🚀 Received analysis request for: " + url);
//
//            // NOTE: Add your clone/commit-extraction logic here.
//            // Once cloned, get the local file path.
//            String localPath = "C:\\path\\to\\your\\cloned\\repo"; // Replace with your logic
//
//            // Trigger the Vector Indexer
//            vectorIndexingService.indexRepository(localPath, url);
//
//            return ResponseEntity.ok("Repository indexed successfully");
//        } catch (Exception e) {
//            e.printStackTrace();
//            return ResponseEntity.internalServerError().body("Failed to index: " + e.getMessage());
//        }
//    }
//}
//package com.gitinsight.gitinsight_backend.controller;
//
//import com.gitinsight.gitinsight_backend.dto.RepositorySummaryResponse;
//import com.gitinsight.gitinsight_backend.entity.GitRepository;
//import com.gitinsight.gitinsight_backend.repository.GitRepositoryRepository;
//import org.springframework.web.bind.annotation.CrossOrigin;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.List;
//import java.util.stream.Collectors;
//
//@RestController
//@RequestMapping("/api/repositories")
//public class RepositoryController {
//
//    private final GitRepositoryRepository repositoryDataAccessor;
//
//    public RepositoryController(GitRepositoryRepository repositoryDataAccessor) {
//        this.repositoryDataAccessor = repositoryDataAccessor;
//    }
//
//    @GetMapping
//    public List<RepositorySummaryResponse> getAllRepositories() {
//        System.out.println("API Request Received: Fetching all repositories...");
//
//        // 1. Fetch raw entities from the database
//        List<GitRepository> rawEntities = repositoryDataAccessor.findAll();
//
//        // 2. Map the heavy Entities into lightweight DTOs
//        return rawEntities.stream()
//                .map(entity -> RepositorySummaryResponse.builder()
//                        .id(entity.getId())
//                        .name(entity.getName())
//                        .url(entity.getUrl())
//                        .analyzedAt(entity.getCreatedAt())
//                        .build())
//                .collect(Collectors.toList());
//    }
//}