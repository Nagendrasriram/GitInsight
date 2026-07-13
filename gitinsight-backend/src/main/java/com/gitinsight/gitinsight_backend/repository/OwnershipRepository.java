package com.gitinsight.gitinsight_backend.repository;

import com.gitinsight.gitinsight_backend.entity.GitRepository;
import com.gitinsight.gitinsight_backend.entity.Ownership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OwnershipRepository extends JpaRepository<Ownership, Long> {
    List<Ownership> findByRepository(GitRepository repository);
}