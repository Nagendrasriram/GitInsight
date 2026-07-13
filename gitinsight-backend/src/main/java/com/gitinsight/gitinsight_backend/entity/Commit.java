package com.gitinsight.gitinsight_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "commits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Commit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String hash;

    @Column(nullable = false)
    private String author;

    @Column(columnDefinition = "TEXT")
    private String message;

    private LocalDateTime commitDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private GitRepository repository;
}