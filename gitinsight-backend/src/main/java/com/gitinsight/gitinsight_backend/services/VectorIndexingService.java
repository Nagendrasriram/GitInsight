package com.gitinsight.gitinsight_backend.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class VectorIndexingService {

    private static final Logger log = LoggerFactory.getLogger(VectorIndexingService.class);

    private final VectorStore vectorStore;

    public VectorIndexingService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    // =========================================================================
    // GET FILE INVENTORY — returns a readable tree of all files in the repo
    // Used by ProjectInsightService to generate architecture diagrams
    // =========================================================================
    public String getFileInventory(String projectPath) throws IOException {
        File rootDir = new File(projectPath).getCanonicalFile();

        if (!rootDir.exists() || !rootDir.isDirectory()) {
            throw new IllegalArgumentException(
                    "Cannot build inventory — path does not exist: " + rootDir.getAbsolutePath());
        }

        StringBuilder inventory = new StringBuilder();
        inventory.append("File Inventory for: ").append(rootDir.getName()).append("\n");
        inventory.append("=".repeat(50)).append("\n");

        Files.walk(rootDir.toPath())
                .filter(path -> !path.toString().contains(".git"))
                .forEach(path -> {
                    String relative = rootDir.toPath().relativize(path).toString();
                    if (relative.isBlank()) return; // skip root itself
                    String prefix = Files.isDirectory(path) ? "[DIR]  " : "[FILE] ";
                    inventory.append(prefix).append(relative).append("\n");
                });

        log.info("File inventory built for path={} | length={} chars",
                rootDir.getAbsolutePath(), inventory.length());

        return inventory.toString();
    }

    // =========================================================================
    // CLEAR INDEX — removes all existing vectors (use with caution)
    // =========================================================================
    public void clearIndex() {
        log.warn("Clearing entire vector index...");

        List<String> ids = vectorStore
                .similaritySearch(SearchRequest.builder().query("*").topK(1000).build())
                .stream()
                .map(Document::getId)
                .collect(Collectors.toList());

        if (ids.isEmpty()) {
            log.info("Vector index is already empty. Nothing to clear.");
            return;
        }

        vectorStore.delete(ids);
        log.info("Cleared {} documents from vector index.", ids.size());
    }

    // =========================================================================
    // INDEX REPOSITORY — walk files, chunk, tag with URL, store embeddings
    // =========================================================================
    public void indexRepository(String projectPath, String url) throws Exception {

        // Normalize path — fixes Windows double-backslash issues
        File rootDir = new File(projectPath).getCanonicalFile();

        log.info("=== INDEXING START ===");
        log.info("Canonical path     : {}", rootDir.getAbsolutePath());
        log.info("Directory exists   : {}", rootDir.exists());
        log.info("Is directory       : {}", rootDir.isDirectory());

        if (!rootDir.exists() || !rootDir.isDirectory()) {
            throw new IllegalArgumentException(
                    "Path does not exist or is not a directory: " + rootDir.getAbsolutePath());
        }

        // Check for JGit sub-folder trap — auto-resolve if top level is empty
        File[] topLevelFiles = rootDir.listFiles();
        if (topLevelFiles == null || topLevelFiles.length == 0) {
            log.warn("Top-level directory is EMPTY. Checking for JGit sub-folder trap...");
            rootDir = resolveActualRepoRoot(rootDir);
        } else {
            log.info("Top-level contains {} items:", topLevelFiles.length);
            for (File f : topLevelFiles) {
                log.info("  → [{}] {}", f.isDirectory() ? "DIR " : "FILE", f.getName());
            }
        }

        log.info("Final scan root: {}", rootDir.getAbsolutePath());

        List<Document> allDocuments = new ArrayList<>();
        TokenTextSplitter splitter = new TokenTextSplitter();
        final File finalRootDir = rootDir;

        Files.walk(rootDir.toPath())
                .filter(Files::isRegularFile)
                .filter(path -> !path.toString().contains(".git"))
                // No extension whitelist — attempt every file, let catch handle binaries
                .forEach(path -> {
                    String fileName = path.getFileName().toString();
                    try {
                        String content = Files.readString(path);

                        if (content.isBlank()) {
                            log.debug("Skipped empty file: {}", fileName);
                            return;
                        }

                        // Tag every chunk with repo URL and file path for filtered RAG search
                        Map<String, Object> metadata = Map.of(
                                "file_name", fileName,
                                "file_path", finalRootDir.toPath().relativize(path).toString(),
                                "url", url
                        );

                        Document doc = new Document(content, metadata);
                        List<Document> chunks = splitter.apply(List.of(doc));
                        allDocuments.addAll(chunks);

                        log.info("Chunked: {} → {} chunks", fileName, chunks.size());

                    } catch (Exception e) {
                        // Binary files (.class, .jar, images) throw here — safe to skip
                        log.warn("Skipped unreadable/binary file: {}", fileName);
                    }
                });

        log.info("Total chunks ready to save: {}", allDocuments.size());

        if (allDocuments.isEmpty()) {
            log.error("Zero chunks produced. Check above logs for skipped files.");
            throw new RuntimeException(
                    "No indexable content found in repository. Scanned: "
                            + rootDir.getAbsolutePath());
        }

        log.info("Saving {} chunks to vector store for url={}", allDocuments.size(), url);
        vectorStore.add(allDocuments);
        log.info("=== INDEXING COMPLETE — {} chunks stored for url={} ===",
                allDocuments.size(), url);
    }

    // =========================================================================
    // SUB-FOLDER TRAP RESOLVER
    // JGit sometimes clones into: /tmp/gitinsight_123/repo-name/
    // This finds the actual repo root one level deeper automatically
    // =========================================================================
    private File resolveActualRepoRoot(File emptyDir) {
        log.info("Attempting sub-folder resolution inside: {}", emptyDir.getAbsolutePath());

        File[] subDirs = emptyDir.listFiles(File::isDirectory);

        if (subDirs == null || subDirs.length == 0) {
            log.error("No sub-directories found. Clone may have failed silently.");
            return emptyDir;
        }

        for (File sub : subDirs) {
            if (!sub.getName().equals(".git")) {
                log.info("Sub-folder trap resolved! Actual repo root: {}", sub.getAbsolutePath());

                File[] innerFiles = sub.listFiles();
                int count = innerFiles != null ? innerFiles.length : 0;
                log.info("Inner directory contains {} items:", count);
                if (innerFiles != null) {
                    for (File f : innerFiles) {
                        log.info("  → [{}] {}", f.isDirectory() ? "DIR " : "FILE", f.getName());
                    }
                }
                return sub;
            }
        }

        log.warn("Could not resolve sub-folder. Returning original directory.");
        return emptyDir;
    }
}
//package com.gitinsight.gitinsight_backend.services;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.ai.document.Document;
//import org.springframework.ai.transformer.splitter.TokenTextSplitter;
//import org.springframework.ai.vectorstore.SearchRequest;
//import org.springframework.ai.vectorstore.VectorStore;
//import org.springframework.stereotype.Service;
//
//import java.io.File;
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Paths;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//@Service
//public class VectorIndexingService {
//
//    private static final Logger log = LoggerFactory.getLogger(VectorIndexingService.class);
//
//    private static final List<String> INDEXABLE_EXTENSIONS = List.of(
//            ".java", ".js", ".ts", ".jsx", ".tsx", ".py", ".go", ".rs",
//            ".kt", ".scala", ".cs", ".cpp", ".c", ".h", ".rb", ".php",
//            ".html", ".css", ".scss", ".xml", ".yml", ".yaml", ".json",
//            ".md", ".txt", ".properties", ".gradle", ".sql", ".sh"
//    );
//
//    private final VectorStore vectorStore;
//
//    public VectorIndexingService(VectorStore vectorStore) {
//        this.vectorStore = vectorStore;
//    }
//    public String getFileInventory(String projectPath) throws IOException {
//        StringBuilder inventory = new StringBuilder("File Inventory:\n");
//
//        Files.walk(Paths.get(projectPath))
//                .filter(Files::isRegularFile)
//                // ADD THIS FILTER to ignore internal git files
//                .filter(path -> !path.toString().contains(".git"))
//                .forEach(path -> inventory.append(path.getFileName().toString()).append("\n"));
//
//        return inventory.toString();
//    }
//    // =========================================================================
//    // CLEAR INDEX
//    // =========================================================================
//    public void clearIndex() {
//        log.warn("Clearing entire vector index...");
//        List<String> ids = vectorStore
//                .similaritySearch(SearchRequest.builder().query("*").topK(1000).build())
//                .stream()
//                .map(Document::getId)
//                .collect(Collectors.toList());
//
//        if (ids.isEmpty()) {
//            log.info("Vector index is already empty.");
//            return;
//        }
//        vectorStore.delete(ids);
//        log.info("Cleared {} documents from vector index.", ids.size());
//    }
//
//    // =========================================================================
//    // INDEX REPOSITORY — with deep debugging + sub-folder auto-detection
//    // =========================================================================
//    public void indexRepository(String projectPath, String url) throws Exception {
//
//        File rootDir = new File(projectPath).getCanonicalFile();
//
//        log.info("=== INDEXING START ===");
//        log.info("Canonical path: {}", rootDir.getAbsolutePath());
//
//        if (!rootDir.exists() || !rootDir.isDirectory()) {
//            throw new IllegalArgumentException(
//                    "Path does not exist or is not a directory: " + rootDir.getAbsolutePath());
//        }
//
//        File[] topLevelFiles = rootDir.listFiles();
//        if (topLevelFiles == null || topLevelFiles.length == 0) {
//            rootDir = resolveActualRepoRoot(rootDir);
//        } else {
//            log.info("Top-level contains {} items:", topLevelFiles.length);
//            for (File f : topLevelFiles) {
//                log.info("  → [{}] {}", f.isDirectory() ? "DIR " : "FILE", f.getName());
//            }
//        }
//
//        List<Document> allDocuments = new ArrayList<>();
//        TokenTextSplitter splitter = new TokenTextSplitter();
//        final File finalRootDir = rootDir;
//
//        Files.walk(rootDir.toPath())
//                .filter(Files::isRegularFile)
//                .filter(path -> !path.toString().contains(".git"))
//                // ✅ NO isIndexable() filter — we try everything
//                .forEach(path -> {
//                    String fileName = path.getFileName().toString();
//                    try {
//                        // Try to read as text — if it's a binary, this throws an exception
//                        String content = Files.readString(path);
//
//                        if (content.isBlank()) {
//                            log.debug("Skipped empty file: {}", fileName);
//                            return;
//                        }
//
//                        Map<String, Object> metadata = Map.of(
//                                "file_name", fileName,
//                                "file_path", finalRootDir.toPath().relativize(path).toString(),
//                                "url", url
//                        );
//
//                        Document doc = new Document(content, metadata);
//                        List<Document> chunks = splitter.apply(List.of(doc));
//                        allDocuments.addAll(chunks);
//
//                        log.info("Chunked: {} → {} chunks", fileName, chunks.size());
//
//                    } catch (Exception e) {
//                        // Binary files (images, .class, .jar) will throw here — safe to skip
//                        log.warn("Skipped unreadable file: {}", fileName);
//                    }
//                });
//
//        log.info("Total chunks ready to save: {}", allDocuments.size());
//
//        if (allDocuments.isEmpty()) {
//            log.error("Zero chunks produced.");
//            throw new RuntimeException(
//                    "No indexable content found. Scanned: " + rootDir.getAbsolutePath());
//        }
//
//        log.info("Saving {} chunks to vector store for url={}", allDocuments.size(), url);
//        vectorStore.add(allDocuments);
//        log.info("=== INDEXING COMPLETE — {} chunks stored ===", allDocuments.size());
//    }
//
//    // =========================================================================
//    // SUB-FOLDER TRAP RESOLVER
//    // JGit sometimes clones into: /tmp/gitinsight_123/repo-name/
//    // This method finds the actual repo root automatically
//    // =========================================================================
//    private File resolveActualRepoRoot(File emptyDir) {
//        log.info("Attempting sub-folder resolution inside: {}", emptyDir.getAbsolutePath());
//
//        File[] subDirs = emptyDir.listFiles(File::isDirectory);
//
//        if (subDirs == null || subDirs.length == 0) {
//            log.error("No sub-directories found either. Clone may have failed silently.");
//            return emptyDir; // return original, will fail with clear error
//        }
//
//        // Pick the first sub-directory that is not .git
//        for (File sub : subDirs) {
//            if (!sub.getName().equals(".git")) {
//                log.info("Sub-folder trap detected! Actual repo root found at: {}", sub.getAbsolutePath());
//                File[] innerFiles = sub.listFiles();
//                log.info("Inner directory contains {} items:", innerFiles != null ? innerFiles.length : 0);
//                if (innerFiles != null) {
//                    for (File f : innerFiles) {
//                        log.info("  → [{}] {}", f.isDirectory() ? "DIR " : "FILE", f.getName());
//                    }
//                }
//                return sub;
//            }
//        }
//
//        log.warn("Could not resolve sub-folder. Returning original directory.");
//        return emptyDir;
//    }
//
//    // =========================================================================
//// HELPER — accept known extensions OR files with no extension at all
//// =========================================================================
//    private boolean isIndexable(String fileName) {
//        String lower = fileName.toLowerCase();
//
//        // Accept known text-based extensions
//        boolean hasKnownExtension = INDEXABLE_EXTENSIONS.stream().anyMatch(lower::endsWith);
//        if (hasKnownExtension) return true;
//
//        // Accept files with NO extension at all (e.g. "121. Best Time to Buy and Sell Stock")
//        // These are common in DSA/competitive programming repos
//        boolean hasNoExtension = !fileName.contains(".");
//        if (hasNoExtension) return true;
//
//        return false;
//    }
//}