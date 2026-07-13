package com.gitinsight.gitinsight_backend.services;

import com.gitinsight.gitinsight_backend.entity.Commit;
import com.gitinsight.gitinsight_backend.repository.CommitRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HealthScoreService {

    private final CommitRepository commitRepository;

    public HealthScoreService(CommitRepository commitRepository) {
        this.commitRepository = commitRepository;
    }

    public int calculateHealthScore(String repoUrl) {
        List<Commit> commits = commitRepository.findByRepositoryUrl(repoUrl);
        List<CommitRepository.ContributorProjection> contributors = commitRepository.findTopContributorsByRepoUrl(repoUrl);

        if (commits.isEmpty()) {
            return 0; // No data means 0 health
        }

        int score = 0;

        // 1. Activity Metric (Max 40 points)
        // 50+ commits is considered "healthy activity" for a small project
        int commitCount = commits.size();
        score += Math.min(40, (int) ((commitCount / 50.0) * 40));

        // 2. Collaboration Metric (Max 40 points)
        // 3+ contributors is considered "healthy collaboration"
        int contributorCount = contributors.size();
        score += Math.min(40, (int) ((contributorCount / 3.0) * 40));

        // 3. Risk / Bus Factor Metric (Max 20 points)
        // If the top contributor did less than 60% of the work, it's very healthy.
        // If they did 100%, it's risky (lower score).
        if (!contributors.isEmpty()) {
            double topContributorCommits = contributors.get(0).getCommitCount();
            double percentageByTop = topContributorCommits / commitCount;

            if (percentageByTop < 0.60) {
                score += 20; // Great distribution
            } else if (percentageByTop < 0.85) {
                score += 10; // Okay distribution
            } else {
                score += 5;  // High risk, one person bottleneck
            }
        }

        return Math.min(100, score); // Ensure it never exceeds 100
    }
}