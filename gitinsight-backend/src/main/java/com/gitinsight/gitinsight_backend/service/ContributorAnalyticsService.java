package com.gitinsight.gitinsight_backend.service;

import com.gitinsight.gitinsight_backend.entity.Commit;
import com.gitinsight.gitinsight_backend.entity.Contributor;
import com.gitinsight.gitinsight_backend.entity.GitRepository;
import com.gitinsight.gitinsight_backend.repository.CommitRepository;
import com.gitinsight.gitinsight_backend.repository.ContributorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ContributorAnalyticsService {

    private final CommitRepository commitRepository;
    private final ContributorRepository contributorRepository;

    public ContributorAnalyticsService(CommitRepository commitRepository, ContributorRepository contributorRepository) {
        this.commitRepository = commitRepository;
        this.contributorRepository = contributorRepository;
    }

    @Transactional
    public void generateContributorMetrics(GitRepository repository) {
        System.out.println("Aggregating Contributor Metrics for: " + repository.getName());

        // 1. Fetch all raw commits for this specific repository
        List<Commit> allCommits = commitRepository.findByRepository(repository);

        // 2. The Analytics Engine: Group by Author Name and Count
        Map<String, Long> commitCountsByAuthor = allCommits.stream()
                .collect(Collectors.groupingBy(Commit::getAuthor, Collectors.counting()));

        // 3. Transform the Map into a List of Contributor database entities
        List<Contributor> contributorsToSave = commitCountsByAuthor.entrySet().stream()
                .map(entry -> Contributor.builder()
                        .name(entry.getKey())
                        .commitCount(entry.getValue().intValue())
                        .repository(repository)
                        .build())
                .toList();

        // 4. Batch save the aggregated data to PostgreSQL
        contributorRepository.saveAll(contributorsToSave);
        System.out.println("Successfully generated metrics for " + contributorsToSave.size() + " unique contributors.");
    }
}