package fr.insastronaute.webapi.sts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.sts.domain.StsRun;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Entering a node is timed, and building a combat is timed apart depending on whether the
 * catalogs were already in memory.
 *
 * <p>The first combat of a data version pays for reading and parsing them and every later one
 * does not. Averaged together the two hide each other, which is how a slow first entry stays
 * invisible behind a hundred fast ones.
 */
class EnterNodeMetricsTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final StsGameDataService gameDataService = mock(StsGameDataService.class);
    private final StsAuthoritativeCombatService combatService =
            mock(StsAuthoritativeCombatService.class);
    private final StsRunService runService = new StsRunService(
            mock(fr.insastronaute.webapi.sts.repository.StsRunRepository.class),
            mock(fr.insastronaute.webapi.user.repository.UserRepository.class),
            gameDataService,
            combatService,
            mock(fr.insastronaute.webapi.sts.multiplayer.service.StsPvpService.class),
            objectMapper,
            registry, mock(AutoChallengeTriggerPublisher.class));

    @Test
    void registersATimerForEnteringANode() {
        assertThat(registry.find("sts.run.enter_node").timer())
                .describedAs("the budget for entering a node cannot be held without measuring it")
                .isNotNull();
    }

    @Test
    void measuresAColdCatalogApartFromAWarmOne() throws Exception {
        StsRun run = run();
        when(gameDataService.isCatalogLoaded("v1")).thenReturn(false, true);
        when(combatService.createCombatState(run))
                .thenReturn(objectMapper.readTree("{\"revision\":0}"));

        ReflectionTestUtils.invokeMethod(runService, "createAuthoritativeCombat", run);
        ReflectionTestUtils.invokeMethod(runService, "createAuthoritativeCombat", run);

        assertThat(registry.find("sts.combat.create").tag("catalog", "cold").timer().count())
                .isEqualTo(1);
        assertThat(registry.find("sts.combat.create").tag("catalog", "warm").timer().count())
                .isEqualTo(1);
    }

    @Test
    void refusesToPretendACombatWasBuiltWhenNothingCameBack() {
        StsRun run = run();
        when(gameDataService.isCatalogLoaded("v1")).thenReturn(true);
        when(combatService.createCombatState(run)).thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        ReflectionTestUtils.invokeMethod(runService, "createAuthoritativeCombat", run))
                .isInstanceOf(IllegalStateException.class);
    }

    private static StsRun run() {
        StsRun run = new StsRun();
        ReflectionTestUtils.setField(run, "id", UUID.randomUUID());
        run.setDataVersion("v1");
        return run;
    }
}
