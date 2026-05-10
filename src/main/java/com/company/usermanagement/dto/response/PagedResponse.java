package com.company.usermanagement.dto.response;

import lombok.*;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Generic wrapper for all paginated API responses.
 *
 * Why a custom wrapper over Spring's Page<T>?
 * Spring's Page<T> serializes to a deeply nested JSON object with internal
 * Hibernate metadata. Our wrapper gives a clean, stable API contract
 * that won't break clients if we change our ORM.
 *
 * Usage: PagedResponse.of(userPage, userResponseList)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagedResponse<T> {

    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean last;
    private boolean first;

    /**
     * Factory method — build from a Spring Page + already-mapped content list.
     * We separate the Page metadata from the content because the content
     * has already been mapped from Entity → DTO before calling this.
     */
    public static <T> PagedResponse<T> of(Page<?> page, List<T> content) {
        return PagedResponse.<T>builder()
                .content(content)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .first(page.isFirst())
                .build();
    }
}