package com.fde.transaction.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Processing summary returned after a batch run.
 * Contains counts grouped by final transaction status.
 */
@Data
public class ProcessingSummaryDTO {

    private long totalSubmitted;
    private Map<String, Long> statusCounts = new HashMap<>();
    private long processedAt;

    public ProcessingSummaryDTO() {
        this.processedAt = System.currentTimeMillis();
    }

    public void incrementStatus(String status) {
        statusCounts.merge(status, 1L, Long::sum);
    }
}
