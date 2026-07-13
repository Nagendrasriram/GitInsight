package com.gitinsight.gitinsight_backend.repository;

import com.gitinsight.gitinsight_backend.entity.Contributor;
import com.gitinsight.gitinsight_backend.entity.GitRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContributorRepository extends JpaRepository<Contributor, Long> {
    List<Contributor> findByRepository(GitRepository repository);
}