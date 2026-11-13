package fr.insastronaute.webapi.treasurehunt.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerVaultCodeRepositoryTest {
    @Test
    void reservesARandomCodeAtomically() throws NoSuchMethodException {
        Method method = PlayerVaultCodeRepository.class.getMethod("tryCreateVaultCode",
                UUID.class, UUID.class, UUID.class, String.class, Instant.class);
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT))
                .contains("insert into player_vault_codes (id, user_id, path_id, code, issued_at)")
                .contains("on conflict do nothing");
        assertThat(query.nativeQuery()).isTrue();
        assertThat(method.getAnnotation(Modifying.class)).isNotNull();
    }
}
