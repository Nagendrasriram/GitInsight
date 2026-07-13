package com.gitinsight.gitinsight_backend.dto;

import java.util.List;
import java.util.Map;

public record DashboardResponse(
        String repositoryName,
        String aiSummary,
        int totalCommits,
        List<CommitDto> recentCommits,
        Map<String, Long> dailyCommitCounts,
        int healthScore,
        int busFactor // <-- Added here
) {

    // Records automatically generate getters:
    // You can access it via response.busFactor() instead of getBusFactor()
}