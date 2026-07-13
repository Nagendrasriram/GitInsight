package com.gitinsight.gitinsight_backend.controllers;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class DebugController {

    private final VectorStore vectorStore;

    public DebugController(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @GetMapping("/api/debug/search")
    public List<String> testSearch(@RequestParam String query) {
        List<Document> results = vectorStore.similaritySearch(query);
        return results.stream()
                .map(doc -> "📄 File: " + doc.getMetadata().getOrDefault("source", "unknown")
                        + "\nSnippet: " + doc.getText())
                .toList();
    }
}