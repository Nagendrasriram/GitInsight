package com.gitinsight.gitinsight_backend.controllers;

import com.gitinsight.gitinsight_backend.services.VectorIndexingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/index")
@CrossOrigin(origins = "*")
public class IndexingController {

    private static final Logger log = LoggerFactory.getLogger(IndexingController.class);

    private final VectorIndexingService vectorIndexingService;

    public IndexingController(VectorIndexingService vectorIndexingService) {
        this.vectorIndexingService = vectorIndexingService;
    }
    // Inside IndexingController.java

    @PostMapping("/run")
// 1. Add 'url' to the RequestParam list
    public String runIndexing(@RequestParam String projectPath, @RequestParam String url) {
        try {
            // 2. Now pass BOTH arguments to the service
            vectorIndexingService.indexRepository(projectPath, url);

            return "✅ Repository indexed successfully!";
        } catch (Exception e) {
            return "❌ Error indexing: " + e.getMessage();
        }
    }
    // POST /api/index/run?projectPath=...&url=...
//    @PostMapping("/run")
//    public String runIndexing(
//            @RequestParam String projectPath,
//            @RequestParam String url) {
//        try {
//            log.info("Manual index triggered → path={} | url={}", projectPath, url);
//            vectorIndexingService.indexRepository(projectPath, url);
//            return "Repository indexed successfully!";
//        } catch (Exception e) {
//            log.error("Indexing failed: {}", e.getMessage(), e);
//            return "Error indexing: " + e.getMessage();
//        }
//    }
}