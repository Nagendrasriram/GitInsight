package com.gitinsight.gitinsight_backend.services;

import com.gitinsight.gitinsight_backend.repository.CommitRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class BusFactorService {
    private final CommitRepository commitRepository;

    public BusFactorService(CommitRepository commitRepository) {
        this.commitRepository = commitRepository;
    }

    public int calculateBusFactor(String repoUrl) {
        // Fetch contributor stats
        var contributors = commitRepository.findTopContributorsByRepoUrl(repoUrl);
        if (contributors.isEmpty()) return 0;

        long totalCommits = contributors.stream()
                .mapToLong(CommitRepository.ContributorProjection::getCommitCount)
                .sum();

        // Count how many contributors account for 80% of the knowledge
        double threshold = totalCommits * 0.8;
        long currentTotal = 0;
        int busFactor = 0;

        for (var c : contributors) {
            currentTotal += c.getCommitCount();
            busFactor++;
            if (currentTotal >= threshold) break;
        }

        return busFactor;
    }
}