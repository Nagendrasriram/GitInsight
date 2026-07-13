package com.gitinsight.gitinsight_backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "contributors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contributor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private Integer commitCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private GitRepository repository;
}