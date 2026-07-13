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
public class CommitResponse {
    private String hash;
    private String author;
    private String message;
    private LocalDateTime commitDate;
}