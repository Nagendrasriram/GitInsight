package com.gitinsight.gitinsight_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepositorySummaryResponse {
    private Long id;
    private String name;
    private String url;
    private LocalDateTime analyzedAt;
}