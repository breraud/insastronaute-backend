package fr.insastronaute.webapi.common.web;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void fromPreservesTheExistingPageContract() {
        PageRequest pageable = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageImpl<String> page = new PageImpl<>(List.of("first", "second"), pageable, 5);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).containsExactly("first", "second");
        assertThat(response.pageable()).isEqualTo(pageable);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.last()).isFalse();
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.number()).isEqualTo(1);
        assertThat(response.sort()).isEqualTo(pageable.getSort());
        assertThat(response.first()).isFalse();
        assertThat(response.numberOfElements()).isEqualTo(2);
        assertThat(response.empty()).isFalse();
    }
}
