package com.gitinsight.gitinsight_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnershipResponse {
    private String filePath;
    private String contributorName;
    private Integer changeCount;
}