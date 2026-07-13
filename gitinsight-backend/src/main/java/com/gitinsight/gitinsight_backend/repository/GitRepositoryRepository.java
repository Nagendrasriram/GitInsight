package com.gitinsight.gitinsight_backend.repository;

import com.gitinsight.gitinsight_backend.entity.GitRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface GitRepositoryRepository extends JpaRepository<GitRepository, Long> {
    Optional<GitRepository> findByUrl(String url);

//    java.util.Optional<GitRepository> findByUrl(String url);
}
