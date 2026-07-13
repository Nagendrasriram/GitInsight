package com.gitinsight.gitinsight_backend.repository;

import com.gitinsight.gitinsight_backend.entity.Commit;
import com.gitinsight.gitinsight_backend.entity.GitRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommitRepository extends JpaRepository<Commit, Long> {
    void deleteByRepositoryId(Long repositoryId);
    List<Commit> findByRepository(GitRepository repository);
    List<Commit> findByRepositoryId(Long repositoryId);

    // --- 1. Top Contributors (Robust Fuzzy Match) ---
    @Query("SELECT c.author as author, COUNT(c) as commitCount " +
            "FROM Commit c WHERE REPLACE(c.repository.url, '/', '') LIKE CONCAT('%', REPLACE(:url, '/', ''), '%') " +
            "GROUP BY c.author ORDER BY commitCount DESC")
    List<ContributorProjection> findTopContributorsByRepoUrl(@Param("url") String url);

    // --- 2. Recent Commits (Robust Fuzzy Match) ---
    @Query("SELECT c FROM Commit c WHERE REPLACE(c.repository.url, '/', '') LIKE CONCAT('%', REPLACE(:url, '/', ''), '%') ORDER BY c.id DESC")
    List<Commit> findByRepositoryUrl(@Param("url") String url);

    interface ContributorProjection {
        String getAuthor();
        Long getCommitCount();
    }
}