package com.gitinsight.gitinsight_backend.dto;

public record CommitDto(
        String author,
        String message,
        String date
) {}