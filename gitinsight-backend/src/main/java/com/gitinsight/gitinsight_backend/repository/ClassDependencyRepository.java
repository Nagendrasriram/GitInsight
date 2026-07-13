package com.gitinsight.gitinsight_backend.repository;

import com.gitinsight.gitinsight_backend.entity.ClassDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClassDependencyRepository extends JpaRepository<ClassDependency, Long> {

    // Find all class dependencies for a specific repository
    List<ClassDependency> findByRepositoryId(Long repositoryId);

    // Delete old dependencies when we re-analyze a repository
    void deleteByRepositoryId(Long repositoryId);
}