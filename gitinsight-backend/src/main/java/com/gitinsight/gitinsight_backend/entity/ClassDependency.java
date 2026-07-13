package com.gitinsight.gitinsight_backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "class_dependencies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private GitRepository repository;

    @Column(nullable = false)
    private String className;

    @Column(nullable = false)
    private String filePath;

    // We store the list of imports/dependencies as a long text string (comma separated)
    @Column(columnDefinition = "TEXT")
    private String dependencies;
}