package com.edusync.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Uniform pagination envelope for every list endpoint.
 *
 * WHY: the audits (API-05/06/07, API-03/04/05) found every list endpoint
 * returning a bare, unbounded `List<T>` with no page/size/sort support —
 * a scalability defect that breaks down once a table has more than a few
 * hundred rows. Wrapping Spring Data's {@link Page} in a stable, versioned
 * shape avoids leaking Spring Data's internal `Page` JSON representation
 * (which is verbose and considered unstable across Spring Data versions)
 * directly into the public API contract.
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean hasNext
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }
}
