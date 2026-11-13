package fr.insastronaute.webapi.sts.multiplayer.repository;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StsPvpMatchmakingLockMigrationTest {

    @Test
    void migrationCreatesAndSeedsTheSingletonMatchmakingMutex() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V91__sts_pvp_matchmaking_lock.sql"));

        assertThat(sql).containsIgnoringCase("CREATE TABLE sts_pvp_matchmaking_lock");
        assertThat(sql).containsIgnoringCase("INSERT INTO sts_pvp_matchmaking_lock");
    }
}
