package fr.insastronaute.webapi.common.web;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public record PageResponse<T>(
        List<T> content,
        Pageable pageable,
        boolean last,
        int totalPages,
        long totalElements,
        int size,
        int number,
        Sort sort,
        boolean first,
        int numberOfElements,
        boolean empty
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getPageable(),
                page.isLast(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.getSize(),
                page.getNumber(),
                page.getSort(),
                page.isFirst(),
                page.getNumberOfElements(),
                page.isEmpty()
        );
    }
}
