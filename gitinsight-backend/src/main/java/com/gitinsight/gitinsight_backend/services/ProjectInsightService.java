package com.gitinsight.gitinsight_backend.services;

import com.gitinsight.gitinsight_backend.dto.CommitDto;
import com.gitinsight.gitinsight_backend.dto.DashboardResponse;
import com.gitinsight.gitinsight_backend.entity.Commit;
import com.gitinsight.gitinsight_backend.entity.GitRepository;
import com.gitinsight.gitinsight_backend.repository.CommitRepository;
import com.gitinsight.gitinsight_backend.repository.GitRepositoryRepository;
import com.gitinsight.gitinsight_backend.service.CommitExtractionService;
import com.gitinsight.gitinsight_backend.service.GitCloningService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProjectInsightService {

    private final CommitRepository commitRepository;
    private final GitRepositoryRepository gitRepositoryRepository;
    private final AiOrchestrationService aiOrchestrationService;
    private final GitCloningService gitCloningService;
    private final CommitExtractionService commitExtractionService;
    private final HealthScoreService healthScoreService;
    private final VectorIndexingService vectorIndexingService;
    private final BusFactorService busFactorService; // <-- NEW

    public ProjectInsightService(CommitRepository commitRepository,
                                 GitRepositoryRepository gitRepositoryRepository,
                                 AiOrchestrationService aiOrchestrationService,
                                 GitCloningService gitCloningService,
                                 CommitExtractionService commitExtractionService,
                                 HealthScoreService healthScoreService,
                                 VectorIndexingService vectorIndexingService,
                                 BusFactorService busFactorService) { // <-- NEW
        this.commitRepository = commitRepository;
        this.gitRepositoryRepository = gitRepositoryRepository;
        this.aiOrchestrationService = aiOrchestrationService;
        this.gitCloningService = gitCloningService;
        this.commitExtractionService = commitExtractionService;
        this.healthScoreService = healthScoreService;
        this.vectorIndexingService = vectorIndexingService;
        this.busFactorService = busFactorService; // <-- NEW
    }

    // =========================================================================
    // DASHBOARD SUMMARY — fetches repo, extracts commits if needed, calls AI
    // =========================================================================
    public DashboardResponse generateRepositorySummaryByUrl(String url) {
        var repository = gitRepositoryRepository.findByUrl(url)
                .orElseGet(() -> {
                    GitRepository newlyClonedRepo = gitCloningService.cloneAndRegister(url);
                    commitExtractionService.extractAndSaveCommits(newlyClonedRepo);
                    return newlyClonedRepo;
                });

        // Calculate bus factor here and pass it down
        int busFactor = busFactorService.calculateBusFactor(url);

        return processCommitsAndCallAi(repository, busFactor);
    }

    // =========================================================================
    // ARCHITECTURE DIAGRAM — generates Mermaid.js diagram from file inventory
    // =========================================================================
    public String generateArchitectureDiagram(String url) {
        GitRepository repository = gitRepositoryRepository.findByUrl(url)
                .orElseThrow(() -> new RuntimeException(
                        "Repository not found in DB. Please run /analyze first for: " + url));

        String inventory;
        try {
            inventory = vectorIndexingService.getFileInventory(repository.getLocalPath());
        } catch (Exception e) {
            inventory = "File inventory unavailable: " + e.getMessage();
        }

        String masterPrompt = """
                You are an enterprise System Architect. Analyze the provided project file inventory and compile a clean, professional architecture diagram using Mermaid.js syntax.
                
                STRICT RULES:
                1. Detect the programming language(s) from the file extensions (e.g., .java, .py, .ts, .go, .rb, .cs) and adapt your layering accordingly.
                2. Group structural elements into logical layers using subgraphs (e.g., Controllers/Routes, Services/Logic, Repositories/DAOs, Entities/Models, Config).
                3. Render explicit directional relationships showing dependencies (e.g., Controller --> Service --> Repository).
                4. Return ONLY a valid, plain text Mermaid diagram string starting with 'graph TD'.
                5. CRITICAL: Do NOT wrap the output in markdown code blocks like ```mermaid or ```. Return purely the raw diagram string.
                6. Do NOT add any explanation text before or after the diagram.
                
                [FILE INVENTORY]
                """ + inventory;

        String raw = aiOrchestrationService.generateAnalysis(
                "You are a strict Mermaid.js compiler. Output only raw graph notation, nothing else.",
                masterPrompt
        );

        return raw.replace("```mermaid", "")
                .replace("```", "")
                .trim();
    }

    // =========================================================================
    // PRIVATE — builds DashboardResponse from commits + AI summary + busFactor
    // =========================================================================
    private DashboardResponse processCommitsAndCallAi(GitRepository repository, int busFactor) {
        List<Commit> allCommits = commitRepository.findByRepositoryId(repository.getId());
        int healthScore = healthScoreService.calculateHealthScore(repository.getUrl());

        if (allCommits.isEmpty()) {
            return new DashboardResponse(
                    repository.getName(), "No commits found.", 0, List.of(), Map.of(), healthScore, busFactor);
        }

        // Recent commits for UI timeline (last 20 for richer AI context)
        List<CommitDto> recentCommitDtos = allCommits.stream()
                .limit(20)
                .map(c -> new CommitDto(c.getAuthor(), c.getMessage(), c.getCommitDate().toString()))
                .toList();

        // Daily commit counts for heatmap
        Map<String, Long> dailyCommitCounts = allCommits.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getCommitDate().toLocalDate().toString(),
                        Collectors.counting()
                ));

        // Build structured commit context for AI (author + date + message)
        StringBuilder contextBuilder = new StringBuilder();
        recentCommitDtos.forEach(dto ->
                contextBuilder
                        .append("- [").append(dto.author()).append("] ")
                        .append("(").append(dto.date()).append("): ")
                        .append(dto.message()).append("\n")
        );

        // Pull file inventory so AI knows the actual project structure
        String inventoryContext;
        try {
            inventoryContext = vectorIndexingService.getFileInventory(repository.getLocalPath());
        } catch (Exception e) {
            inventoryContext = "File inventory unavailable.";
        }

        // ── SYSTEM PROMPT ─────────────────────────────────────────────────────
        String systemPrompt = """
                You are an elite Engineering Manager and Software Architect performing a technical code review audit.
                You will receive two inputs: a list of recent git commits and the project's file/folder inventory.
                
                YOUR MISSION:
                - Cross-reference the commit messages with the actual file structure to understand what was truly built, changed, or fixed.
                - Detect the primary language(s) and framework(s) from the file extensions and folder names (e.g., pom.xml = Java/Maven, requirements.txt = Python, package.json = Node.js/TypeScript, go.mod = Go).
                - Infer the architectural pattern in use (e.g., MVC, layered architecture, microservices, REST API, ML pipeline).
                - Do NOT hallucinate features. Only report what the evidence — commits + files — actually supports.
                
                OUTPUT FORMAT — respond in exactly these four Markdown sections, no more, no less:
                
                ### 🏗️ Project Overview
                One crisp paragraph: what this project is, what language/framework it uses, and what architectural pattern it follows. Be specific — name actual files or folders as evidence.
                
                ### 🚀 Architectural Progress
                Bullet points only. What layers, modules, or features were built or modified in these commits? Reference actual file names or directories where possible. Skip vague entries like "updated code" — if the commit message is uninformative, infer from the file inventory.
                
                ### 🛠️ Engineering Hygiene & Risk
                Bullet points only. Assess commit message quality, test coverage signals, documentation presence, and any structural red flags visible in the file tree. Be honest and specific.
                
                ### 🎯 Next Milestone Recommendations
                Exactly 3 bullet points. Concrete, actionable next steps tailored to this specific project's stack and current state. Not generic advice — name the actual layers or files that need attention.
                
                TONE: Direct. Technical. Zero filler. Write like a senior engineer reviewing a colleague's PR, not a consultant writing a report.
                """;

        // ── FINAL USER PAYLOAD ────────────────────────────────────────────────
        String finalUserPayload = """
                [RECENT GIT COMMITS — last %d commits, total %d in repo]
                %s
                
                [PROJECT FILE INVENTORY]
                %s
                """.formatted(recentCommitDtos.size(), allCommits.size(),
                contextBuilder.toString(), inventoryContext);

        String aiSummary = aiOrchestrationService.generateAnalysis(systemPrompt, finalUserPayload);

        // Return only the first 10 commits to the UI timeline (display limit)
        List<CommitDto> displayCommits = recentCommitDtos.stream().limit(10).toList();

        return new DashboardResponse(
                repository.getName(),
                aiSummary,
                allCommits.size(),
                displayCommits,
                dailyCommitCounts,
                healthScore,
                busFactor); // <-- NEW
    }
}

//package com.gitinsight.gitinsight_backend.services;
//
//import com.gitinsight.gitinsight_backend.dto.CommitDto;
//import com.gitinsight.gitinsight_backend.dto.DashboardResponse;
//import com.gitinsight.gitinsight_backend.entity.Commit;
//import com.gitinsight.gitinsight_backend.entity.GitRepository;
//import com.gitinsight.gitinsight_backend.repository.CommitRepository;
//import com.gitinsight.gitinsight_backend.repository.GitRepositoryRepository;
//import com.gitinsight.gitinsight_backend.service.CommitExtractionService;
//import com.gitinsight.gitinsight_backend.service.GitCloningService;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//@Service
//public class ProjectInsightService {
//
//    private final CommitRepository commitRepository;
//    private final GitRepositoryRepository gitRepositoryRepository;
//    private final AiOrchestrationService aiOrchestrationService;
//    private final GitCloningService gitCloningService;
//    private final CommitExtractionService commitExtractionService;
//    private final HealthScoreService healthScoreService;
//    private final VectorIndexingService vectorIndexingService;
//
//    public ProjectInsightService(CommitRepository commitRepository,
//                                 GitRepositoryRepository gitRepositoryRepository,
//                                 AiOrchestrationService aiOrchestrationService,
//                                 GitCloningService gitCloningService,
//                                 CommitExtractionService commitExtractionService,
//                                 HealthScoreService healthScoreService,
//                                 VectorIndexingService vectorIndexingService) {
//        this.commitRepository = commitRepository;
//        this.gitRepositoryRepository = gitRepositoryRepository;
//        this.aiOrchestrationService = aiOrchestrationService;
//        this.gitCloningService = gitCloningService;
//        this.commitExtractionService = commitExtractionService;
//        this.healthScoreService = healthScoreService;
//        this.vectorIndexingService = vectorIndexingService;
//    }
//
//    // =========================================================================
//    // DASHBOARD SUMMARY — fetches repo, extracts commits if needed, calls AI
//    // =========================================================================
//    public DashboardResponse generateRepositorySummaryByUrl(String url) {
//        var repository = gitRepositoryRepository.findByUrl(url)
//                .orElseGet(() -> {
//                    GitRepository newlyClonedRepo = gitCloningService.cloneAndRegister(url);
//                    commitExtractionService.extractAndSaveCommits(newlyClonedRepo);
//                    return newlyClonedRepo;
//                });
//
//        return processCommitsAndCallAi(repository);
//    }
//
//    // =========================================================================
//    // ARCHITECTURE DIAGRAM — generates Mermaid.js diagram from file inventory
//    // =========================================================================
//    public String generateArchitectureDiagram(String url) {
//        GitRepository repository = gitRepositoryRepository.findByUrl(url)
//                .orElseThrow(() -> new RuntimeException(
//                        "Repository not found in DB. Please run /analyze first for: " + url));
//
//        String inventory;
//        try {
//            inventory = vectorIndexingService.getFileInventory(repository.getLocalPath());
//        } catch (Exception e) {
//            inventory = "File inventory unavailable: " + e.getMessage();
//        }
//
//        String masterPrompt = """
//                You are an enterprise System Architect. Analyze the provided project file inventory and compile a clean, professional architecture diagram using Mermaid.js syntax.
//
//                STRICT RULES:
//                1. Detect the programming language(s) from the file extensions (e.g., .java, .py, .ts, .go, .rb, .cs) and adapt your layering accordingly.
//                2. Group structural elements into logical layers using subgraphs (e.g., Controllers/Routes, Services/Logic, Repositories/DAOs, Entities/Models, Config).
//                3. Render explicit directional relationships showing dependencies (e.g., Controller --> Service --> Repository).
//                4. Return ONLY a valid, plain text Mermaid diagram string starting with 'graph TD'.
//                5. CRITICAL: Do NOT wrap the output in markdown code blocks like ```mermaid or ```. Return purely the raw diagram string.
//                6. Do NOT add any explanation text before or after the diagram.
//
//                [FILE INVENTORY]
//                """ + inventory;
//
//        String raw = aiOrchestrationService.generateAnalysis(
//                "You are a strict Mermaid.js compiler. Output only raw graph notation, nothing else.",
//                masterPrompt
//        );
//
//        return raw.replace("```mermaid", "")
//                .replace("```", "")
//                .trim();
//    }
//
//    // =========================================================================
//    // PRIVATE — builds DashboardResponse from commits + AI summary
//    // =========================================================================
//    private DashboardResponse processCommitsAndCallAi(GitRepository repository) {
//        List<Commit> allCommits = commitRepository.findByRepositoryId(repository.getId());
//        int healthScore = healthScoreService.calculateHealthScore(repository.getUrl());
//
//        if (allCommits.isEmpty()) {
//            return new DashboardResponse(
//                    repository.getName(), "No commits found.", 0, List.of(), Map.of(), healthScore);
//        }
//
//        // Recent commits for UI timeline (last 20 for richer AI context)
//        List<CommitDto> recentCommitDtos = allCommits.stream()
//                .limit(20)
//                .map(c -> new CommitDto(c.getAuthor(), c.getMessage(), c.getCommitDate().toString()))
//                .toList();
//
//        // Daily commit counts for heatmap
//        Map<String, Long> dailyCommitCounts = allCommits.stream()
//                .collect(Collectors.groupingBy(
//                        c -> c.getCommitDate().toLocalDate().toString(),
//                        Collectors.counting()
//                ));
//
//        // Build structured commit context for AI (author + date + message)
//        StringBuilder contextBuilder = new StringBuilder();
//        recentCommitDtos.forEach(dto ->
//                contextBuilder
//                        .append("- [").append(dto.author()).append("] ")
//                        .append("(").append(dto.date()).append("): ")
//                        .append(dto.message()).append("\n")
//        );
//
//        // Pull file inventory so AI knows the actual project structure
//        String inventoryContext;
//        try {
//            inventoryContext = vectorIndexingService.getFileInventory(repository.getLocalPath());
//        } catch (Exception e) {
//            inventoryContext = "File inventory unavailable.";
//        }
//
//        // ── SYSTEM PROMPT ─────────────────────────────────────────────────────
//        // Language-agnostic: works for Java, Python, TypeScript, Go, Ruby, etc.
//        String systemPrompt = """
//                You are an elite Engineering Manager and Software Architect performing a technical code review audit.
//                You will receive two inputs: a list of recent git commits and the project's file/folder inventory.
//
//                YOUR MISSION:
//                - Cross-reference the commit messages with the actual file structure to understand what was truly built, changed, or fixed.
//                - Detect the primary language(s) and framework(s) from the file extensions and folder names (e.g., pom.xml = Java/Maven, requirements.txt = Python, package.json = Node.js/TypeScript, go.mod = Go).
//                - Infer the architectural pattern in use (e.g., MVC, layered architecture, microservices, REST API, ML pipeline).
//                - Do NOT hallucinate features. Only report what the evidence — commits + files — actually supports.
//
//                OUTPUT FORMAT — respond in exactly these four Markdown sections, no more, no less:
//
//                ### 🏗️ Project Overview
//                One crisp paragraph: what this project is, what language/framework it uses, and what architectural pattern it follows. Be specific — name actual files or folders as evidence.
//
//                ### 🚀 Architectural Progress
//                Bullet points only. What layers, modules, or features were built or modified in these commits? Reference actual file names or directories where possible. Skip vague entries like "updated code" — if the commit message is uninformative, infer from the file inventory.
//
//                ### 🛠️ Engineering Hygiene & Risk
//                Bullet points only. Assess commit message quality, test coverage signals, documentation presence, and any structural red flags visible in the file tree. Be honest and specific.
//
//                ### 🎯 Next Milestone Recommendations
//                Exactly 3 bullet points. Concrete, actionable next steps tailored to this specific project's stack and current state. Not generic advice — name the actual layers or files that need attention.
//
//                TONE: Direct. Technical. Zero filler. Write like a senior engineer reviewing a colleague's PR, not a consultant writing a report.
//                """;
//
//        // ── FINAL USER PAYLOAD ────────────────────────────────────────────────
//        String finalUserPayload = """
//                [RECENT GIT COMMITS — last %d commits, total %d in repo]
//                %s
//
//                [PROJECT FILE INVENTORY]
//                %s
//                """.formatted(recentCommitDtos.size(), allCommits.size(),
//                contextBuilder.toString(), inventoryContext);
//
//        String aiSummary = aiOrchestrationService.generateAnalysis(systemPrompt, finalUserPayload);
//
//        // Return only the first 10 commits to the UI timeline (display limit)
//        List<CommitDto> displayCommits = recentCommitDtos.stream().limit(10).toList();
//
//        return new DashboardResponse(
//                repository.getName(), aiSummary, allCommits.size(),
//                displayCommits, dailyCommitCounts, healthScore);
//    }
//}



//package com.gitinsight.gitinsight_backend.services;
//
//import com.gitinsight.gitinsight_backend.dto.CommitDto;
//import com.gitinsight.gitinsight_backend.dto.DashboardResponse;
//import com.gitinsight.gitinsight_backend.entity.Commit;
//import com.gitinsight.gitinsight_backend.entity.GitRepository;
//import com.gitinsight.gitinsight_backend.repository.CommitRepository;
//import com.gitinsight.gitinsight_backend.repository.GitRepositoryRepository;
//import com.gitinsight.gitinsight_backend.service.CommitExtractionService;
//import com.gitinsight.gitinsight_backend.service.GitCloningService;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//@Service
//public class ProjectInsightService {
//
//    private final CommitRepository commitRepository;
//    private final GitRepositoryRepository gitRepositoryRepository;
//    private final AiOrchestrationService aiOrchestrationService;
//    private final GitCloningService gitCloningService;
//    private final CommitExtractionService commitExtractionService;
//    private final HealthScoreService healthScoreService;
//    private final VectorIndexingService vectorIndexingService;
//
//    public ProjectInsightService(CommitRepository commitRepository,
//                                 GitRepositoryRepository gitRepositoryRepository,
//                                 AiOrchestrationService aiOrchestrationService,
//                                 GitCloningService gitCloningService,
//                                 CommitExtractionService commitExtractionService,
//                                 HealthScoreService healthScoreService,
//                                 VectorIndexingService vectorIndexingService) {
//        this.commitRepository = commitRepository;
//        this.gitRepositoryRepository = gitRepositoryRepository;
//        this.aiOrchestrationService = aiOrchestrationService;
//        this.gitCloningService = gitCloningService;
//        this.commitExtractionService = commitExtractionService;
//        this.healthScoreService = healthScoreService;
//        this.vectorIndexingService = vectorIndexingService;
//    }
//
//    // =========================================================================
//    // DASHBOARD SUMMARY — fetches repo, extracts commits if needed, calls AI
//    // =========================================================================
//    public DashboardResponse generateRepositorySummaryByUrl(String url) {
//        var repository = gitRepositoryRepository.findByUrl(url)
//                .orElseGet(() -> {
//                    GitRepository newlyClonedRepo = gitCloningService.cloneAndRegister(url);
//                    commitExtractionService.extractAndSaveCommits(newlyClonedRepo);
//                    return newlyClonedRepo;
//                });
//
//        return processCommitsAndCallAi(repository);
//    }
//
//    // =========================================================================
//    // ARCHITECTURE DIAGRAM — generates Mermaid.js diagram from file inventory
//    // =========================================================================
//    public String generateArchitectureDiagram(String url) {
//        GitRepository repository = gitRepositoryRepository.findByUrl(url)
//                .orElseThrow(() -> new RuntimeException(
//                        "Repository not found in DB. Please run /analyze first for: " + url));
//
//        // Get file inventory from local cloned path
//        String inventory;
//        try {
//            inventory = vectorIndexingService.getFileInventory(repository.getLocalPath());
//        } catch (Exception e) {
//            inventory = "File inventory unavailable: " + e.getMessage();
//        }
//
//        String masterPrompt = """
//                You are an enterprise System Architect. Analyze the provided project file inventory and compile a clean, professional architecture diagram using Mermaid.js syntax.
//
//                STRICT RULES:
//                1. Group structural elements into logical layers using subgraphs (e.g., Controllers, Services, Repositories, Entities, Config).
//                2. Render explicit directional relationships showing dependencies (e.g., Controller --> Service --> Repository).
//                3. Return ONLY a valid, plain text Mermaid diagram string starting with 'graph TD'.
//                4. CRITICAL: Do NOT wrap the output in markdown code blocks like ```mermaid or ```. Return purely the raw diagram string.
//                5. Do NOT add any explanation text before or after the diagram.
//
//                [FILE INVENTORY]
//                """ + inventory;
//
//        String raw = aiOrchestrationService.generateAnalysis(
//                "You are a strict Mermaid.js compiler. Output only raw graph notation, nothing else.",
//                masterPrompt
//        );
//
//        // Safety cleanup — strip any markdown backticks the AI adds despite instructions
//        return raw.replace("```mermaid", "")
//                .replace("```", "")
//                .trim();
//    }
//
//    // =========================================================================
//    // PRIVATE — builds DashboardResponse from commits + AI summary
//    // =========================================================================
//    private DashboardResponse processCommitsAndCallAi(GitRepository repository) {
//        List<Commit> allCommits = commitRepository.findByRepositoryId(repository.getId());
//        int healthScore = healthScoreService.calculateHealthScore(repository.getUrl());
//
//        if (allCommits.isEmpty()) {
//            return new DashboardResponse(
//                    repository.getName(), "No commits found.", 0, List.of(), Map.of(), healthScore);
//        }
//
//        // Recent commits for UI timeline
//        List<CommitDto> recentCommitDtos = allCommits.stream()
//                .limit(10)
//                .map(c -> new CommitDto(c.getAuthor(), c.getMessage(), c.getCommitDate().toString()))
//                .toList();
//
//        // Daily commit counts for heatmap
//        Map<String, Long> dailyCommitCounts = allCommits.stream()
//                .collect(Collectors.groupingBy(
//                        c -> c.getCommitDate().toLocalDate().toString(),
//                        Collectors.counting()
//                ));
//
//        // Build AI context from recent commits
//        StringBuilder contextBuilder = new StringBuilder();
//        recentCommitDtos.forEach(dto ->
//                contextBuilder.append("- [").append(dto.author()).append("]: ").append(dto.message()).append("\n"));
//
//        String aiSummary = aiOrchestrationService.generateAnalysis(
//                "You are a Senior Engineering Manager. Summarize recent work.",
//                contextBuilder.toString()
//        );
//
//        return new DashboardResponse(
//                repository.getName(), aiSummary, allCommits.size(),
//                recentCommitDtos, dailyCommitCounts, healthScore);
//    }
//}


//package com.gitinsight.gitinsight_backend.services;
//
//import com.gitinsight.gitinsight_backend.dto.CommitDto;
//import com.gitinsight.gitinsight_backend.dto.DashboardResponse;
//import com.gitinsight.gitinsight_backend.entity.Commit;
//import com.gitinsight.gitinsight_backend.entity.GitRepository;
//import com.gitinsight.gitinsight_backend.repository.CommitRepository;
//import com.gitinsight.gitinsight_backend.repository.GitRepositoryRepository;
//import com.gitinsight.gitinsight_backend.service.GitCloningService;
//import com.gitinsight.gitinsight_backend.service.CommitExtractionService;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//@Service
//public class ProjectInsightService {
//
//    private final CommitRepository commitRepository;
//    private final GitRepositoryRepository gitRepositoryRepository;
//    private final AiOrchestrationService aiOrchestrationService;
//    private final GitCloningService gitCloningService;
//    private final CommitExtractionService commitExtractionService;
//
//    // 1. Declare the new HealthScoreService
//    private final HealthScoreService healthScoreService;
//
//    public ProjectInsightService(CommitRepository commitRepository,
//                                 GitRepositoryRepository gitRepositoryRepository,
//                                 AiOrchestrationService aiOrchestrationService,
//                                 GitCloningService gitCloningService,
//                                 CommitExtractionService commitExtractionService,
//                                 HealthScoreService healthScoreService) { // Add to constructor
//        this.commitRepository = commitRepository;
//        this.gitRepositoryRepository = gitRepositoryRepository;
//        this.aiOrchestrationService = aiOrchestrationService;
//        this.gitCloningService = gitCloningService;
//        this.commitExtractionService = commitExtractionService;
//        this.healthScoreService = healthScoreService; // Assign it
//    }
//
//    public DashboardResponse generateRepositorySummaryByUrl(String url) {
//        var repository = gitRepositoryRepository.findByUrl(url)
//                .orElseGet(() -> {
//                    GitRepository newlyClonedRepo = gitCloningService.cloneAndRegister(url);
//                    commitExtractionService.extractAndSaveCommits(newlyClonedRepo);
//                    return newlyClonedRepo;
//                });
//
//        return processCommitsAndCallAi(repository);
//    }
//
//    private DashboardResponse processCommitsAndCallAi(GitRepository repository) {
//        List<Commit> allCommits = commitRepository.findByRepositoryId(repository.getId());
//
//        // 2. Calculate the health score right here!
//        int healthScore = healthScoreService.calculateHealthScore(repository.getUrl());
//
//        if (allCommits.isEmpty()) {
//            // Added healthScore as the 6th argument here
//            return new DashboardResponse(repository.getName(), "No commits found.", 0, List.of(), Map.of(), healthScore);
//        }
//
//        // Map recent commits for the UI timeline
//        List<CommitDto> recentCommitDtos = allCommits.stream()
//                .limit(10)
//                .map(c -> new CommitDto(c.getAuthor(), c.getMessage(), c.getCommitDate().toString()))
//                .toList();
//
//        // AGGREGATE: Group commits by Date (String) and Count them
//        Map<String, Long> dailyCommitCounts = allCommits.stream()
//                .collect(Collectors.groupingBy(
//                        c -> c.getCommitDate().toLocalDate().toString(),
//                        Collectors.counting()
//                ));
//
//        // AI Analysis
//        StringBuilder contextBuilder = new StringBuilder();
//        recentCommitDtos.forEach(dto -> contextBuilder.append("- [").append(dto.author()).append("]: ").append(dto.message()).append("\n"));
//
//        String aiSummary = aiOrchestrationService.generateAnalysis("You are a Senior Engineering Manager. Summarize recent work.", contextBuilder.toString());
//
//        // Added healthScore as the 6th argument here!
//        return new DashboardResponse(repository.getName(), aiSummary, allCommits.size(), recentCommitDtos, dailyCommitCounts, healthScore);
//    }
//}
//package com.gitinsight.gitinsight_backend.services;
//
//import com.gitinsight.gitinsight_backend.dto.CommitDto;
//import com.gitinsight.gitinsight_backend.dto.DashboardResponse;
//import com.gitinsight.gitinsight_backend.entity.Commit;
//import com.gitinsight.gitinsight_backend.entity.GitRepository;
//import com.gitinsight.gitinsight_backend.repository.CommitRepository;
//import com.gitinsight.gitinsight_backend.repository.GitRepositoryRepository;
//import com.gitinsight.gitinsight_backend.service.GitCloningService;
//import com.gitinsight.gitinsight_backend.service.CommitExtractionService;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//@Service
//public class ProjectInsightService {
//
//    private final CommitRepository commitRepository;
//    private final GitRepositoryRepository gitRepositoryRepository;
//    private final AiOrchestrationService aiOrchestrationService;
//    private final GitCloningService gitCloningService;
//    private final CommitExtractionService commitExtractionService;
//
//    public ProjectInsightService(CommitRepository commitRepository,
//                                 GitRepositoryRepository gitRepositoryRepository,
//                                 AiOrchestrationService aiOrchestrationService,
//                                 GitCloningService gitCloningService,
//                                 CommitExtractionService commitExtractionService) {
//        this.commitRepository = commitRepository;
//        this.gitRepositoryRepository = gitRepositoryRepository;
//        this.aiOrchestrationService = aiOrchestrationService;
//        this.gitCloningService = gitCloningService;
//        this.commitExtractionService = commitExtractionService;
//    }
//
//    public DashboardResponse generateRepositorySummaryByUrl(String url) {
//        var repository = gitRepositoryRepository.findByUrl(url)
//                .orElseGet(() -> {
//                    GitRepository newlyClonedRepo = gitCloningService.cloneAndRegister(url);
//                    commitExtractionService.extractAndSaveCommits(newlyClonedRepo);
//                    return newlyClonedRepo;
//                });
//
//        return processCommitsAndCallAi(repository);
//    }
////    // ADD THIS TO ProjectInsightService.java
////    public DashboardResponse generateRepositorySummary(Long repositoryId) {
////        var repository = gitRepositoryRepository.findById(repositoryId)
////                .orElseThrow(() -> new RuntimeException("Repository not found in database."));
////
////        return processCommitsAndCallAi(repository);
////    }
//
//    private DashboardResponse processCommitsAndCallAi(GitRepository repository) {
//        List<Commit> allCommits = commitRepository.findByRepositoryId(repository.getId());
//
//        if (allCommits.isEmpty()) {
//            return new DashboardResponse(repository.getName(), "No commits found.", 0, List.of(), Map.of());
//        }
//
//        // 1. Map recent commits for the UI timeline
//        List<CommitDto> recentCommitDtos = allCommits.stream()
//                .limit(10)
//                .map(c -> new CommitDto(c.getAuthor(), c.getMessage(), c.getCommitDate().toString()))
//                .toList();
//
//        // 2. AGGREGATE: Group commits by Date (String) and Count them
//        Map<String, Long> dailyCommitCounts = allCommits.stream()
//                .collect(Collectors.groupingBy(
//                        c -> c.getCommitDate().toLocalDate().toString(),
//                        Collectors.counting()
//                ));
//
//        // 3. AI Analysis
//        StringBuilder contextBuilder = new StringBuilder();
//        recentCommitDtos.forEach(dto -> contextBuilder.append("- [").append(dto.author()).append("]: ").append(dto.message()).append("\n"));
//
//        String aiSummary = aiOrchestrationService.generateAnalysis("You are a Senior Engineering Manager. Summarize recent work.", contextBuilder.toString());
//
//        // 4. Return the complete package including the new 'dailyCommitCounts'
//        return new DashboardResponse(repository.getName(), aiSummary, allCommits.size(), recentCommitDtos, dailyCommitCounts);
//    }
//}




//package com.gitinsight.gitinsight_backend.services;
//
//import com.gitinsight.gitinsight_backend.entity.Commit;
//import com.gitinsight.gitinsight_backend.entity.GitRepository;
//import com.gitinsight.gitinsight_backend.repository.CommitRepository;
//import com.gitinsight.gitinsight_backend.repository.GitRepositoryRepository;
//import com.gitinsight.gitinsight_backend.service.GitCloningService;
//// 1. Import your CommitExtractionService
//import com.gitinsight.gitinsight_backend.service.CommitExtractionService;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//
//@Service
//public class ProjectInsightService {
//
//    private final CommitRepository commitRepository;
//    private final GitRepositoryRepository gitRepositoryRepository;
//    private final AiOrchestrationService aiOrchestrationService;
//    private final GitCloningService gitCloningService;
//
//    // 2. Declare the CommitExtractionService
//    private final CommitExtractionService commitExtractionService;
//
//    // 3. Update the constructor
//    public ProjectInsightService(CommitRepository commitRepository,
//                                 GitRepositoryRepository gitRepositoryRepository,
//                                 AiOrchestrationService aiOrchestrationService,
//                                 GitCloningService gitCloningService,
//                                 CommitExtractionService commitExtractionService) {
//        this.commitRepository = commitRepository;
//        this.gitRepositoryRepository = gitRepositoryRepository;
//        this.aiOrchestrationService = aiOrchestrationService;
//        this.gitCloningService = gitCloningService;
//        this.commitExtractionService = commitExtractionService;
//    }
//
//    public String generateRepositorySummary(Long repositoryId) {
//        var repository = gitRepositoryRepository.findById(repositoryId)
//                .orElseThrow(() -> new RuntimeException("Repository not found in database."));
//
//        return processCommitsAndCallAi(repository);
//    }
//
//    public String generateRepositorySummaryByUrl(String url) {
//        var repository = gitRepositoryRepository.findByUrl(url)
//                .orElseGet(() -> {
//                    System.out.println("⚠️ Repository not in database. Initiating JGit cloning...");
//
//                    // Step A: Clone the repository
//                    GitRepository newlyClonedRepo = gitCloningService.cloneAndRegister(url);
//                    System.out.println("✅ Successfully cloned: " + newlyClonedRepo.getName());
//
//                    // Step B: EXTRACT THE COMMITS! 🚀
//                    System.out.println("⏳ Extracting commits to database...");
//
//                    // NOTE: Change 'extractCommits' to whatever the method is actually named inside your CommitExtractionService!
//                    commitExtractionService.extractAndSaveCommits(newlyClonedRepo);
//
//                    System.out.println("✅ Commits extracted!");
//
//                    return newlyClonedRepo;
//                });
//
//        return processCommitsAndCallAi(repository);
//    }
//
//    private String processCommitsAndCallAi(GitRepository repository) {
//        List<Commit> recentCommits = commitRepository.findByRepositoryId(repository.getId());
//
//        if (recentCommits.isEmpty()) {
//            return "No commits found for this repository.";
//        }
//
//        StringBuilder contextBuilder = new StringBuilder();
//        contextBuilder.append("Repository Name: ").append(repository.getName()).append("\n");
//        contextBuilder.append("Recent Commits:\n");
//
//        for (int i = 0; i < Math.min(recentCommits.size(), 10); i++) {
//            Commit c = recentCommits.get(i);
//            contextBuilder.append("- [").append(c.getAuthor()).append("]: ")
//                    .append(c.getMessage()).append("\n");
//        }
//
//        String systemInstruction = "You are a Senior Engineering Manager. " +
//                "Review the provided git commit history and write a short, professional paragraph " +
//                "summarizing what features or fixes the team has been working on recently.";
//
//        System.out.println("🧠 Sending real JGit data to AI for repository: " + repository.getName());
//        return aiOrchestrationService.generateAnalysis(systemInstruction, contextBuilder.toString());
//    }
//}
