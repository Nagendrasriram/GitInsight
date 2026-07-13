package com.gitinsight.gitinsight_backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ownership")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ownership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String filePath;

    @Column(nullable = false)
    private String contributorName;

    private Integer changeCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private GitRepository repository;
}