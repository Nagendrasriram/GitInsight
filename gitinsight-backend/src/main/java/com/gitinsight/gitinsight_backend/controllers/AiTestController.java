//package com.gitinsight.gitinsight_backend.controllers;
//
//import com.gitinsight.gitinsight_backend.dto.DashboardResponse;
//import com.gitinsight.gitinsight_backend.services.ProjectInsightService;
//import com.gitinsight.gitinsight_backend.services.CodeReviewService; // Imported here!
//import org.springframework.web.bind.annotation.*;
//@CrossOrigin(origins = "http://localhost:5173")
//@RequestMapping("/api/ai-test")
//@RestController
//public class AiTestController {
//
//    private final ProjectInsightService projectInsightService;
//    private final CodeReviewService codeReviewService;
//
//    public AiTestController(ProjectInsightService projectInsightService,
//                            CodeReviewService codeReviewService) {
//        this.projectInsightService = projectInsightService;
//        this.codeReviewService = codeReviewService;
//    }
//
//    // THIS IS THE ONLY ONE YOU NEED FOR YOUR FRONTEND
//    @GetMapping("/api/repositories/analyze")
//    public DashboardResponse getSummaryByUrl(@RequestParam String url) {
//        return projectInsightService.generateRepositorySummaryByUrl(url);
//    }
//
//    @GetMapping("/api/repositories/code-review")
//    public String getDeepCodeReview(@RequestParam String url) {
//        return codeReviewService.performDeepCodeReview(url);
//    }
//}