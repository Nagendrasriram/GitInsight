package com.gitinsight.gitinsight_backend.services;

import com.gitinsight.gitinsight_backend.entity.ClassDependency;
import com.gitinsight.gitinsight_backend.entity.Commit;
import com.gitinsight.gitinsight_backend.entity.GitRepository;
import com.gitinsight.gitinsight_backend.repository.ClassDependencyRepository;
import com.gitinsight.gitinsight_backend.repository.CommitRepository;
import com.gitinsight.gitinsight_backend.repository.GitRepositoryRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final ClassDependencyRepository classDependencyRepository;
    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final CommitRepository commitRepository;
    private final GitRepositoryRepository gitRepositoryRepository;
    private final VectorIndexingService vectorIndexingService;

    public ChatService(VectorStore vectorStore,
                       ChatClient chatClient,
                       CommitRepository commitRepository,
                       GitRepositoryRepository gitRepositoryRepository,
                       VectorIndexingService vectorIndexingService,
                       ClassDependencyRepository classDependencyRepository) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.commitRepository = commitRepository;
        this.gitRepositoryRepository = gitRepositoryRepository;
        this.vectorIndexingService = vectorIndexingService;
        this.classDependencyRepository = classDependencyRepository;
    }

    public String askCodebase(String question, String url) {
        String decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8);

        // ── URL Sanitization (fixes metadata filter mismatch) ─────────────────
        // The vector store saves chunks with the .git URL, but the frontend may
        // send the URL without .git. We normalize here so the filter always matches.
        String cleanUrl = decodedUrl;
        if (cleanUrl.contains("/tree/")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.indexOf("/tree/"));
        }
        if (!cleanUrl.endsWith(".git")) {
            cleanUrl = cleanUrl + ".git";
        }
        // ──────────────────────────────────────────────────────────────────────

        // 1. Fetch repository record using the clean, normalized URL
        GitRepository repo = gitRepositoryRepository.findByUrl(cleanUrl).orElse(null);

        // 2. Get File Inventory (the structural "map" of the codebase)
        String inventory = "Unknown";
        if (repo != null && repo.getLocalPath() != null) {
            try {
                inventory = vectorIndexingService.getFileInventory(repo.getLocalPath());
            } catch (Exception e) {
                inventory = "Could not retrieve file inventory.";
            }
        }

        // 3. Fetch Knowledge Graph — AST-parsed class dependency map
        StringBuilder graphContextBuilder = new StringBuilder();
        if (repo != null) {
            List<ClassDependency> dependencies = classDependencyRepository.findByRepositoryId(repo.getId());
            if (!dependencies.isEmpty()) {
                for (ClassDependency cd : dependencies) {
                    if (cd.getDependencies() != null && !cd.getDependencies().isEmpty()) {
                        graphContextBuilder.append("- Class [")
                                .append(cd.getClassName())
                                .append("] explicitly imports/depends on: ")
                                .append(cd.getDependencies())
                                .append("\n");
                    }
                }
            } else {
                graphContextBuilder.append("No explicit Java dependencies mapped.");
            }
        } else {
            graphContextBuilder.append("Repository not found for dependency mapping.");
        }
        final String dependencyGraphContext = graphContextBuilder.toString();

        // 4. Vector Similarity Search — uses cleanUrl so the filter matches stored metadata
        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(5)
                        .filterExpression("url == '" + cleanUrl + "'")
                        .build()
        );

        String codeContext = similarDocuments.isEmpty()
                ? "NO_CODE_FOUND"
                : similarDocuments.stream()
                  .map(Document::getText)
                  .collect(Collectors.joining("\n---\n"));

        // 5. SQL Metadata — commit history and contributors, keyed by cleanUrl
        List<CommitRepository.ContributorProjection> contributors = commitRepository.findTopContributorsByRepoUrl(cleanUrl);
        List<Commit> allCommits = commitRepository.findByRepositoryUrl(cleanUrl);
        String metadataContext = formatMetadata(cleanUrl, allCommits, contributors);

        // Effectively final references for lambda capture
        final String inventoryForPrompt = inventory;

        // 6. Master Prompt — four knowledge sources wired in
        String systemPrompt = """
                You are GitInsight AI, an expert Senior Software Architect. Use the provided REPOSITORY METADATA, FILE INVENTORY, DEPENDENCY GRAPH, and CODE SNIPPETS to answer the user's question accurately.
                
                ### KNOWLEDGE SOURCES
                1. REPOSITORY METADATA: History, contributors, commit activity.
                2. FILE INVENTORY: List of files in the project. Use this if asked about project structure or what files exist.
                3. DEPENDENCY GRAPH: Exact AST-parsed class relationships (who imports who). Use this as absolute truth for architectural dependency questions.
                4. CODE SNIPPETS: Actual logic, implementation details, and architecture from the vector store.
                
                ### RULES
                - If asked what breaks when a file is modified, RELY STRICTLY ON THE DEPENDENCY GRAPH. Do not guess.
                - If CODE SNIPPETS is "NO_CODE_FOUND", tell the user the vector search returned no results and suggest re-running /analyze.
                - Never hallucinate code or class names. Only cite what exists in the provided sources.
                - Use professional, well-structured Markdown in every response.
                - If a question is answerable from the DEPENDENCY GRAPH or FILE INVENTORY alone, answer it — do not block on missing code snippets.
                
                [FILE INVENTORY]
                {inventory}
                
                [DEPENDENCY GRAPH]
                {dependencyGraph}
                
                [REPOSITORY METADATA]
                {metadata}
                
                [CODE SNIPPETS]
                {context}
                """;

        return chatClient.prompt()
                .system(s -> s
                        .param("inventory",        inventoryForPrompt)
                        .param("dependencyGraph",  dependencyGraphContext)
                        .param("metadata",         metadataContext)
                        .param("context",          codeContext)
                        .text(systemPrompt))
                .user(question)
                .call()
                .content();
    }

    private String formatMetadata(String url, List<Commit> commits, List<CommitRepository.ContributorProjection> contributors) {
        StringBuilder sb = new StringBuilder();
        sb.append("Repository: ").append(url).append("\n");
        sb.append("Total Commits: ").append(commits.size()).append("\n\n=== TOP CONTRIBUTORS ===\n");
        if (contributors.isEmpty()) {
            sb.append("No contributor data available.\n");
        } else {
            contributors.forEach(c ->
                    sb.append("- ").append(c.getAuthor())
                            .append(": ").append(c.getCommitCount()).append(" commits\n")
            );
        }
        return sb.toString();
    }
}



//package com.gitinsight.gitinsight_backend.services;
//
//import com.gitinsight.gitinsight_backend.entity.ClassDependency;
//import com.gitinsight.gitinsight_backend.entity.Commit;
//import com.gitinsight.gitinsight_backend.entity.GitRepository;
//import com.gitinsight.gitinsight_backend.repository.ClassDependencyRepository;
//import com.gitinsight.gitinsight_backend.repository.CommitRepository;
//import com.gitinsight.gitinsight_backend.repository.GitRepositoryRepository;
//import org.springframework.ai.chat.client.ChatClient;
//import org.springframework.ai.document.Document;
//import org.springframework.ai.vectorstore.SearchRequest;
//import org.springframework.ai.vectorstore.VectorStore;
//import org.springframework.stereotype.Service;
//
//import java.net.URLDecoder;
//import java.nio.charset.StandardCharsets;
//import java.util.List;
//import java.util.stream.Collectors;
//
//@Service
//public class ChatService {
//
//    private final ClassDependencyRepository classDependencyRepository;
//    private final VectorStore vectorStore;
//    private final ChatClient chatClient;
//    private final CommitRepository commitRepository;
//    private final GitRepositoryRepository gitRepositoryRepository;
//    private final VectorIndexingService vectorIndexingService;
//
//    // UPDATE 1: Inject ClassDependencyRepository into the constructor
//    public ChatService(VectorStore vectorStore,
//                       ChatClient chatClient,
//                       CommitRepository commitRepository,
//                       GitRepositoryRepository gitRepositoryRepository,
//                       VectorIndexingService vectorIndexingService,
//                       ClassDependencyRepository classDependencyRepository) {
//        this.vectorStore = vectorStore;
//        this.chatClient = chatClient;
//        this.commitRepository = commitRepository;
//        this.gitRepositoryRepository = gitRepositoryRepository;
//        this.vectorIndexingService = vectorIndexingService;
//        this.classDependencyRepository = classDependencyRepository;
//    }
//
//    public String askCodebase(String question, String url) {
//        String decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8);
//
//        // Fetch repository early so we can use its ID
//        GitRepository repo = gitRepositoryRepository.findByUrl(decodedUrl).orElse(null);
//
//        // 1. Get File Inventory (The "Map" of the codebase)
//        String inventory = "Unknown";
//        if (repo != null && repo.getLocalPath() != null) {
//            try {
//                inventory = vectorIndexingService.getFileInventory(repo.getLocalPath());
//            } catch (Exception e) {
//                inventory = "Could not retrieve file inventory.";
//            }
//        }
//
//        // UPDATE 2: Fetch Knowledge Graph Dependencies (The AST map)
//        StringBuilder graphContextBuilder = new StringBuilder();
//        if (repo != null) {
//            List<ClassDependency> dependencies = classDependencyRepository.findByRepositoryId(repo.getId());
//            if (!dependencies.isEmpty()) {
//                for (ClassDependency cd : dependencies) {
//                    if (cd.getDependencies() != null && !cd.getDependencies().isEmpty()) {
//                        graphContextBuilder.append("- Class [")
//                                .append(cd.getClassName())
//                                .append("] explicitly imports/depends on: ")
//                                .append(cd.getDependencies())
//                                .append("\n");
//                    }
//                }
//            } else {
//                graphContextBuilder.append("No explicit Java dependencies mapped.");
//            }
//        } else {
//            graphContextBuilder.append("Repository not found for dependency mapping.");
//        }
//        final String dependencyGraphContext = graphContextBuilder.toString();
//
//        // 3. Vector Similarity Search
//        List<Document> similarDocuments = vectorStore.similaritySearch(
//                SearchRequest.builder()
//                        .query(question)
//                        .topK(5)
//                        .filterExpression("url == '" + decodedUrl + "'")
//                        .build()
//        );
//
//        String codeContext = similarDocuments.isEmpty()
//                ? "NO_CODE_FOUND"
//                : similarDocuments.stream()
//                  .map(Document::getText)
//                  .collect(Collectors.joining("\n---\n"));
//
//        // 4. SQL Metadata Retrieval
//        List<CommitRepository.ContributorProjection> contributors = commitRepository.findTopContributorsByRepoUrl(decodedUrl);
//        List<Commit> allCommits = commitRepository.findByRepositoryUrl(decodedUrl);
//        String metadataContext = formatMetadata(decodedUrl, allCommits, contributors);
//
//        // Create effectively final variables for the lambda
//        final String inventoryForPrompt = inventory;
//
//        // UPDATE 3: Master Prompt now includes the DEPENDENCY GRAPH
//        String systemPrompt = """
//                You are GitInsight AI, an expert Senior Software Architect. Use the provided REPOSITORY METADATA, FILE INVENTORY, DEPENDENCY GRAPH, and CODE SNIPPETS to answer the user's question accurately.
//
//                ### KNOWLEDGE SOURCES
//                1. REPOSITORY METADATA: History, contributors, commit activity.
//                2. FILE INVENTORY: List of files in the project. Use this if asked about project structure.
//                3. DEPENDENCY GRAPH: Exact AST-parsed class relationships (who imports who). Use this as absolute truth for architectural dependency questions.
//                4. CODE SNIPPETS: Logic, implementation, and architecture.
//
//                ### RULES
//                - If asked what breaks when a file is modified, RELY STRICTLY ON THE DEPENDENCY GRAPH. Do not guess.
//                - If CODE SNIPPETS is "NO_CODE_FOUND", inform the user they need to analyze the repo.
//                - Use professional Markdown.
//
//                [FILE INVENTORY]
//                {inventory}
//
//                [DEPENDENCY GRAPH]
//                {dependencyGraph}
//
//                [REPOSITORY METADATA]
//                {metadata}
//
//                [CODE SNIPPETS]
//                {context}
//                """;
//
//        return chatClient.prompt()
//                .system(s -> s
//                        .param("inventory", inventoryForPrompt)
//                        .param("dependencyGraph", dependencyGraphContext) // UPDATE 4: Inject the variable here
//                        .param("metadata", metadataContext)
//                        .param("context", codeContext)
//                        .text(systemPrompt))
//                .user(question)
//                .call()
//                .content();
//    }
//
//    private String formatMetadata(String url, List<Commit> commits, List<CommitRepository.ContributorProjection> contributors) {
//        StringBuilder sb = new StringBuilder();
//        sb.append("Repository: ").append(url).append("\n");
//        sb.append("Total Commits: ").append(commits.size()).append("\n\n=== TOP CONTRIBUTORS ===\n");
//        if (contributors.isEmpty()) sb.append("No data.\n");
//        else contributors.forEach(c -> sb.append("- ").append(c.getAuthor()).append(": ").append(c.getCommitCount()).append(" commits\n"));
//        return sb.toString();
//    }
//}



//package com.gitinsight.gitinsight_backend.services;
//
//import com.gitinsight.gitinsight_backend.entity.Commit;
//import com.gitinsight.gitinsight_backend.entity.GitRepository;
//import com.gitinsight.gitinsight_backend.repository.ClassDependencyRepository;
//import com.gitinsight.gitinsight_backend.repository.CommitRepository;
//import com.gitinsight.gitinsight_backend.repository.GitRepositoryRepository;
//import org.springframework.ai.chat.client.ChatClient;
//import org.springframework.ai.document.Document;
//import org.springframework.ai.vectorstore.SearchRequest;
//import org.springframework.ai.vectorstore.VectorStore;
//import org.springframework.stereotype.Service;
//
//import java.net.URLDecoder;
//import java.nio.charset.StandardCharsets;
//import java.util.List;
//import java.util.stream.Collectors;
//
//@Service
//public class ChatService {
//
//    // Add this to your ChatService constructor
//    private final VectorStore vectorStore;
//    private final ChatClient chatClient;
//    private final CommitRepository commitRepository;
//    private final GitRepositoryRepository gitRepositoryRepository;
//    private final VectorIndexingService vectorIndexingService;
//
//    public ChatService(VectorStore vectorStore,
//                       ChatClient chatClient,
//                       CommitRepository commitRepository,
//                       GitRepositoryRepository gitRepositoryRepository,
//                       VectorIndexingService vectorIndexingService) {
//        this.vectorStore = vectorStore;
//        this.chatClient = chatClient;
//        this.commitRepository = commitRepository;
//        this.gitRepositoryRepository = gitRepositoryRepository;
//        this.vectorIndexingService = vectorIndexingService;
//    }
//
//    public String askCodebase(String question, String url) {
//        String decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8);
//
//        // 1. Get File Inventory (The "Map" of the codebase)
//        String inventory = "Unknown";
//        try {
//            GitRepository repo = gitRepositoryRepository.findByUrl(decodedUrl).orElse(null);
//            if (repo != null && repo.getLocalPath() != null) {
//                inventory = vectorIndexingService.getFileInventory(repo.getLocalPath());
//            }
//        } catch (Exception e) {
//            inventory = "Could not retrieve file inventory.";
//        }
//
//        // 2. Vector Similarity Search
//        List<Document> similarDocuments = vectorStore.similaritySearch(
//                SearchRequest.builder()
//                        .query(question)
//                        .topK(5)
//                        .filterExpression("url == '" + decodedUrl + "'")
//                        .build()
//        );
//
//        String codeContext = similarDocuments.isEmpty()
//                ? "NO_CODE_FOUND"
//                : similarDocuments.stream()
//                  .map(Document::getText)
//                  .collect(Collectors.joining("\n---\n"));
//
//        // 3. SQL Metadata Retrieval
//        List<CommitRepository.ContributorProjection> contributors = commitRepository.findTopContributorsByRepoUrl(decodedUrl);
//        List<Commit> allCommits = commitRepository.findByRepositoryUrl(decodedUrl);
//        String metadataContext = formatMetadata(decodedUrl, allCommits, contributors);
//
//        // --- FIX STARTS HERE ---
//        // Create an effectively final variable for the lambda
//        final String inventoryForPrompt = inventory;
//        // --- FIX ENDS HERE ---
//
//        // 4. Master Prompt
//        String systemPrompt = """
//                You are GitInsight AI. Use the provided REPOSITORY METADATA, FILE INVENTORY, and CODE SNIPPETS.
//
//                ### KNOWLEDGE SOURCES
//                1. REPOSITORY METADATA: History, contributors, commit activity.
//                2. FILE INVENTORY: List of files in the project. Use this if asked about project structure.
//                3. CODE SNIPPETS: Logic, implementation, and architecture.
//
//                ### RULES
//                - If CODE SNIPPETS is "NO_CODE_FOUND", inform the user they need to analyze the repo.
//                - Use professional Markdown.
//
//                [FILE INVENTORY]
//                {inventory}
//
//                [REPOSITORY METADATA]
//                {metadata}
//
//                [CODE SNIPPETS]
//                {context}
//                """;
//
//        return chatClient.prompt()
//                .system(s -> s
//                        .param("inventory", inventoryForPrompt) // Use the final variable here
//                        .param("metadata", metadataContext)
//                        .param("context", codeContext)
//                        .text(systemPrompt))
//                .user(question)
//                .call()
//                .content();
//    }
//
//    private String formatMetadata(String url, List<Commit> commits, List<CommitRepository.ContributorProjection> contributors) {
//        StringBuilder sb = new StringBuilder();
//        sb.append("Repository: ").append(url).append("\n");
//        sb.append("Total Commits: ").append(commits.size()).append("\n\n=== TOP CONTRIBUTORS ===\n");
//        if (contributors.isEmpty()) sb.append("No data.\n");
//        else contributors.forEach(c -> sb.append("- ").append(c.getAuthor()).append(": ").append(c.getCommitCount()).append(" commits\n"));
//        return sb.toString();
//    }
//}





//package com.gitinsight.gitinsight_backend.services;
//
//import com.gitinsight.gitinsight_backend.entity.Commit;
//import com.gitinsight.gitinsight_backend.repository.CommitRepository;
//import com.gitinsight.gitinsight_backend.services.VectorIndexingService;
//import org.springframework.ai.chat.client.ChatClient;
//import org.springframework.ai.document.Document;
//import org.springframework.ai.vectorstore.SearchRequest;
//import org.springframework.ai.vectorstore.VectorStore;
//import org.springframework.stereotype.Service;
//
//import java.net.URLDecoder;
//import java.nio.charset.StandardCharsets;
//import java.util.List;
//import java.util.stream.Collectors;
//
//@Service
//public class ChatService {
//
//    private final VectorStore vectorStore;
//    private final ChatClient chatClient;
//    private final CommitRepository commitRepository;
//    // Inside ChatService.java
////    String inventory = vectorIndexingService.getFileInventory(localPath);
//    String systemPrompt = "You are a senior engineer. Here is the file inventory: " + inventory;
//    // Use this systemPrompt when calling the LLM
//    public ChatService(VectorStore vectorStore, ChatClient chatClient, CommitRepository commitRepository) {
//        this.vectorStore = vectorStore;
//        this.chatClient = chatClient;
//        this.commitRepository = commitRepository;
//    }
//
//    public String askCodebase(String question, String url) {
//        String decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8);
//
//        // ====================================================================
//        // BRAIN 1: VECTOR DATABASE (The "How" and "What")
//        // ====================================================================
//        // Filter logic: This MUST match the metadata key used during indexing!
//        String filter = "url == '" + decodedUrl + "'";
//
//        List<Document> similarDocuments = vectorStore.similaritySearch(
//                SearchRequest.builder()
//                        .query(question)
//                        .topK(5)
//                        .filterExpression(filter)
//                        .build()
//        );
//
//        // DEBUGGING: See what the AI actually finds
//        System.out.println("DEBUG: Searching Vector Store for URL: " + decodedUrl);
//        System.out.println("DEBUG: Found " + similarDocuments.size() + " documents.");
//        similarDocuments.forEach(doc -> System.out.println("DEBUG: Doc Metadata: " + doc.getMetadata()));
//
//        String codeContext = similarDocuments.isEmpty()
//                ? "NO_CODE_FOUND" // We use a flag to signal the AI that index is empty
//                : similarDocuments.stream()
//                  .map(Document::getText)
//                  .collect(Collectors.joining("\n---\n"));
//
//        // ====================================================================
//        // BRAIN 2: SQL DATABASE (The "Who", "When", and "How Much")
//        // ====================================================================
//        List<CommitRepository.ContributorProjection> contributors = commitRepository.findTopContributorsByRepoUrl(decodedUrl);
//        List<Commit> allCommits = commitRepository.findByRepositoryUrl(decodedUrl);
//
//        String metadataContext = formatMetadata(decodedUrl, allCommits, contributors);
//
//        // ====================================================================
//        // MASTER SYSTEM PROMPT
//        // ====================================================================
//        String systemPrompt = """
//                You are GitInsight AI. Your goal is to answer developer questions based on the provided REPOSITORY METADATA and CODE SNIPPETS.
//
//                ### KNOWLEDGE SOURCES
//                1. REPOSITORY METADATA: Use for history, contributors, and commit activity.
//                2. CODE SNIPPETS: Use for logic, implementation, and architecture.
//
//                ### RULES
//                - If CODE SNIPPETS contains "NO_CODE_FOUND", inform the user: 'I do not have enough indexed code to answer this, please ensure the repository has been analyzed.'
//                - If the info is missing from both sources, state that clearly.
//                - Use professional Markdown.
//
//                [REPOSITORY METADATA]
//                {metadata}
//
//                [CODE SNIPPETS]
//                {context}
//                """;
//
//        return chatClient.prompt()
//                .system(s -> s
//                        .param("metadata", metadataContext)
//                        .param("context", codeContext)
//                        .text(systemPrompt))
//                .user(question)
//                .call()
//                .content();
//    }
//
//    private String formatMetadata(String url, List<Commit> commits, List<CommitRepository.ContributorProjection> contributors) {
//        StringBuilder sb = new StringBuilder();
//        sb.append("Repository: ").append(url).append("\n");
//        sb.append("Total Commits: ").append(commits.size()).append("\n\n=== TOP CONTRIBUTORS ===\n");
//        if (contributors.isEmpty()) sb.append("No data.\n");
//        else contributors.forEach(c -> sb.append("- ").append(c.getAuthor()).append(": ").append(c.getCommitCount()).append(" commits\n"));
//        return sb.toString();
//    }
//}