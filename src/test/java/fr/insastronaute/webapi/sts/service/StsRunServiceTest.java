package fr.insastronaute.webapi.sts.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.mock;

import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.sts.domain.StsRun;
import fr.insastronaute.webapi.sts.domain.StsRunStatus;
import fr.insastronaute.webapi.sts.dto.StsCompleteNodeRequest;
import fr.insastronaute.webapi.sts.dto.StsCompleteNodeResponse;
import fr.insastronaute.webapi.sts.dto.StsEnterNodeResponse;
import fr.insastronaute.webapi.sts.dto.StsChooseEventOptionRequest;
import fr.insastronaute.webapi.sts.dto.StsChooseEventOptionResponse;
import fr.insastronaute.webapi.sts.dto.StsClaimRewardRequest;
import fr.insastronaute.webapi.sts.dto.StsRestActionRequest;
import fr.insastronaute.webapi.sts.dto.StsRunStateDto;
import fr.insastronaute.webapi.sts.dto.StsStartRunRequest;
import fr.insastronaute.webapi.sts.dto.StsRetireRunResponse;
import fr.insastronaute.webapi.sts.dto.StsRetreatPreviewResponse;
import fr.insastronaute.webapi.sts.repository.StsRunRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class StsRunServiceTest {

    @Mock
    private StsRunRepository stsRunRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StsGameDataService gameDataService;

    @Mock
    private fr.insastronaute.webapi.sts.service.StsAuthoritativeCombatService stsAuthoritativeCombatService;

    @Mock
    private fr.insastronaute.webapi.sts.multiplayer.service.StsPvpService stsPvpService;

    private ObjectMapper objectMapper;
    private StsRunService stsRunService;
    private AppUser owner;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        stsRunService = new StsRunService(stsRunRepository, userRepository, gameDataService, stsAuthoritativeCombatService, stsPvpService, objectMapper, mock(AutoChallengeTriggerPublisher.class));
        owner = new AppUser();
        owner.setKeycloakSub("sub-1");
    }

    @Test
    void startRunBuildsDeterministicDeckFromCatalogStartingCounts() throws Exception {
        when(userRepository.findWithLockByKeycloakSub(owner.getKeycloakSub())).thenReturn(Optional.of(owner));
        when(gameDataService.latestVersion()).thenReturn("2026-07-10.1");
        when(gameDataService.character("2026-07-10.1", "MECA")).thenReturn(objectMapper.readTree("""
                {
                  "characterId": "MECA",
                  "hp": 70
                }
                """));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "cards": [
                    {
                      "id": "katana",
                      "targetingMode": "Enemy",
                      "favoredCharacter": "Starting",
                      "startingCount": 5
                    },
                    {
                      "id": "ultravibration",
                      "targetingMode": "Player",
                      "favoredCharacter": "MECA",
                      "startingCount": 1
                    },
                    {
                      "id": "architecture",
                      "targetingMode": "Enemy",
                      "favoredCharacter": "AI",
                      "startingCount": 1
                    }
                  ]
                }
                """));
        when(gameDataService.mapGeneration("2026-07-10.1")).thenReturn(minimalMapConfig());
        when(stsRunRepository.save(any(StsRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StsRunStateDto state = stsRunService.startRun(owner, new StsStartRunRequest(
                "MECA",
                "1.0.0"
        ));

        JsonNode deck = json(state.runInventory()).path("deck");
        assertThat(deck).hasSize(6);
        assertThat(deck.findValuesAsText("cardId")).containsExactly(
                "katana",
                "katana",
                "katana",
                "katana",
                "katana",
                "ultravibration"
        );
        assertThat(deck.get(5).path("targetingMode").asText()).isEqualTo("Player");
    }

    @Test
    void startRunReturnsExistingActiveRunWithoutGeneratingAnotherRun() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun existingRun = run(runId);
        when(stsRunRepository.findFirstByOwner_KeycloakSubAndStatusOrderByUpdatedAtDesc(
                owner.getKeycloakSub(),
                StsRunStatus.Active
        )).thenReturn(Optional.of(existingRun));

        StsRunStateDto state = stsRunService.startRun(owner, new StsStartRunRequest("ITI", "1.0.0"));

        assertThat(state.runId()).isEqualTo(runId);
        assertThat(state.selectedCharacter()).isEqualTo("MECA");
        verify(gameDataService, never()).latestVersion();
        verify(stsRunRepository, never()).save(any(StsRun.class));
    }

    @Test
    void startRunReturnsRunCreatedWhileWaitingForOwnerLock() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun concurrentlyCreatedRun = run(runId);
        when(stsRunRepository.findFirstByOwner_KeycloakSubAndStatusOrderByUpdatedAtDesc(
                owner.getKeycloakSub(),
                StsRunStatus.Active
        )).thenReturn(Optional.empty()).thenReturn(Optional.of(concurrentlyCreatedRun));
        when(userRepository.findWithLockByKeycloakSub(owner.getKeycloakSub())).thenReturn(Optional.of(owner));

        StsRunStateDto state = stsRunService.startRun(owner, new StsStartRunRequest("ITI", "1.0.0"));

        assertThat(state.runId()).isEqualTo(runId);
        verify(gameDataService, never()).latestVersion();
        verify(stsRunRepository, never()).save(any(StsRun.class));
    }

    @Test
    void enterNodeReplaysPersistedEncounterWithoutRegeneratingIt() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = run(runId);
        run.setEnteredNodeId(1);
        run.setMapState(objectMapper.readTree("""
                {
                  "nodes": [
                    {"id":0,"floor":0,"type":"Start","visited":true,"completed":true,"nextIds":[1]},
                    {"id":1,"floor":1,"type":"Combat","visited":true,"completed":false,"nextIds":[]}
                  ]
                }
                """));
        JsonNode persistedEncounter = objectMapper.readTree("""
                {"encounterInstanceId":"encinst_existing","encounterId":"combat_1","enemyIds":["enemy_1"]}
                """);
        run.setActiveEncounter(persistedEncounter);
        JsonNode repairedCombat = objectMapper.readTree("""
                {"combatId":"combat_existing","revision":0}
                """);
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(stsAuthoritativeCombatService.createCombatState(run)).thenReturn(repairedCombat);

        StsEnterNodeResponse response = stsRunService.enterNode(owner, runId, 1, null);

        assertThat(json(response.activeEncounter())).isEqualTo(persistedEncounter);
        assertThat(json(response.activeEncounter()).path("encounterInstanceId").asText())
                .isEqualTo("encinst_existing");
        assertThat(json(response.activeCombat())).isEqualTo(repairedCombat);
        assertThat(run.getActiveCombat()).isEqualTo(repairedCombat);
        verify(gameDataService, never()).enemyPool(any());
    }

    @Test
    void enterNodeDoesNotHideAuthoritativeCombatCreationFailure() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = run(runId);
        run.setEnteredNodeId(1);
        run.setActiveEncounter(objectMapper.readTree(
                "{\"encounterInstanceId\":\"encinst_broken\",\"enemyIds\":[\"enemy_1\"]}"));
        run.setMapState(objectMapper.readTree("""
                {
                  "nodes": [
                    {"id":0,"floor":0,"type":"Start","visited":true,"completed":true,"nextIds":[1]},
                    {"id":1,"floor":1,"type":"Combat","visited":true,"completed":false,"nextIds":[]}
                  ]
                }
                """));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(stsAuthoritativeCombatService.createCombatState(run))
                .thenThrow(new IllegalArgumentException("unsupported enemy move"));

        assertThatThrownBy(() -> stsRunService.enterNode(owner, runId, 1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported enemy move");

        assertThat(run.getActiveCombat()).isNull();
    }

    @Test
    void combatSnapshotRepairsPersistedEncounterMissingItsCombat() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = run(runId);
        run.setActiveEncounter(objectMapper.readTree(
                "{\"encounterInstanceId\":\"encinst_repair\",\"enemyIds\":[\"enemy_1\"]}"));
        JsonNode repairedCombat = objectMapper.readTree(
                "{\"combatId\":\"combat_repaired\",\"revision\":0}");
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(stsAuthoritativeCombatService.createCombatState(run)).thenReturn(repairedCombat);

        JsonNode snapshot = stsRunService.authoritativeCombatSnapshot(owner.getKeycloakSub(), runId);

        assertThat(snapshot).isEqualTo(repairedCombat);
        assertThat(run.getActiveCombat()).isEqualTo(repairedCombat);
    }

    @Test
    void eventNodeStoresActiveEventUntilCompletion() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = run(runId);
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.events("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "events": [
                    {
                      "id": "event_shrine",
                      "title": "Ancient Shrine"
                    }
                  ]
                }
                """));

        StsEnterNodeResponse enterResponse = stsRunService.enterNode(owner, runId, 1, null);

        assertThat(enterResponse.nodeType()).isEqualTo("Event");
        assertThat(enterResponse.eventId()).isEqualTo("event_shrine");
        assertThat(json(enterResponse.activeEvent()).path("title").asText()).isEqualTo("Ancient Shrine");
        assertThat(run.getActiveEvent().path("id").asText()).isEqualTo("event_shrine");

        String eventInstanceId = json(enterResponse.activeEvent()).path("eventInstanceId").asText();
        StsEnterNodeResponse replayedResponse = stsRunService.enterNode(owner, runId, 1, null);

        assertThat(json(replayedResponse.activeEvent()).path("eventInstanceId").asText()).isEqualTo(eventInstanceId);
        assertThat(replayedResponse.eventId()).isEqualTo("event_shrine");
        verify(gameDataService, times(1)).events("2026-07-10.1");

        StsRunStateDto state = stsRunService.toDto(run);
        assertThat(json(state.activeEvent()).path("id").asText()).isEqualTo("event_shrine");

        StsCompleteNodeResponse completeResponse = stsRunService.completeNode(owner, runId, 1, null);

        assertThat(json(completeResponse.pendingRewards()).isEmpty()).isTrue();
        assertThat(run.getActiveEvent()).isNull();
        assertThat(json(stsRunService.toDto(run).map()).path("nodes").get(1).path("completed").asBoolean()).isTrue();
    }

    @Test
    void completeNodeRetryReturnsAuthoritativeCompletedState() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = run(runId);
        run.setMapState(objectMapper.readTree("""
                {
                  "nodes": [
                    {"id":0,"floor":0,"type":"Start","visited":true,"completed":true,"nextIds":[1]},
                    {"id":1,"floor":1,"type":"Rest","visited":true,"completed":true,"nextIds":[]}
                  ]
                }
                """));
        run.setCurrentNodeId(1);
        run.setEnteredNodeId(null);
        run.setPendingRewards(objectMapper.readTree("""
                [{"rewardId":"existing_reward","type":"Gold","amount":10,"claimed":false}]
                """));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));

        StsCompleteNodeResponse response = stsRunService.completeNode(owner, runId, 1, null);

        assertThat(response.accepted()).isTrue();
        assertThat(response.currentNodeId()).isEqualTo(1);
        assertThat(json(response.pendingRewards())).isEqualTo(run.getPendingRewards());
        verifyNoInteractions(userRepository);
    }

    @Test
    void completeBossNodeGrantsBossTierRelicReward() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = encounterRun(runId, "Boss", "encinst_boss", List.of("boss_enemy"));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.rewardTables("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "gold": {"baseMinInclusive": 15, "baseMaxInclusive": 15, "bossBonus": 30},
                  "cardChoices": {"choicesPerReward": 1, "eliteCardEnchant": {"enabled": false}},
                  "tokens": {"grantRules": []}
                }
                """));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "cards": [
                    {"id": "katana", "favoredCharacter": "Starting", "targetingMode": "Enemy"}
                  ]
                }
                """));
        when(gameDataService.relics("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "relics": [
                    {"id": "elite_relic", "backendGrantable": true, "rarity": "Rare"},
                    {"id": "boss_relic", "backendGrantable": true, "rarity": "Boss"}
                  ]
                }
                """));

        StsCompleteNodeResponse response = stsRunService.completeNode(
                owner,
                runId,
                1,
                new StsCompleteNodeRequest("encinst_boss", "Victory", 3, 63, 7, List.of("boss_enemy"), null)
        );

        String relicId = null;
        for (JsonNode reward : json(response.pendingRewards())) {
            if ("Relic".equals(reward.path("type").asText())) {
                relicId = reward.path("relicId").asText();
            }
        }

        assertThat(relicId).isEqualTo("boss_relic");
    }

    /**
     * Une élite propose une carte enchantée, et c'est bien un enchantement du jeu : on écrivait
     * ici un « EliteEnchantment » que ni le moteur ni le client ne savaient lire, si bien que la
     * carte s'affichait enchantée dans la récompense et entrait au deck sans rien.
     */
    @Test
    void completeEliteNodeOffersARealEnchantmentOnItsCardChoice() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = encounterRun(runId, "Elite", "encinst_elite", List.of("elite_enemy"));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.rewardTables("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "gold": {"baseMinInclusive": 15, "baseMaxInclusive": 15, "eliteBonus": 10},
                  "cardChoices": {
                    "choicesPerReward": 1,
                    "eliteCardEnchant": {"enabled": true, "minInclusive": 3, "maxInclusive": 3}
                  },
                  "tokens": {"grantRules": []}
                }
                """));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "cards": [
                    {
                      "id": "katana", "cardName": "Katana", "favoredCharacter": "MECA",
                      "cost": 1, "type": "Attaque", "targetingMode": "Enemy",
                      "effects": [{"type": "Damage", "value": 6}]
                    }
                  ]
                }
                """));
        when(gameDataService.relics("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {"relics": []}
                """));

        StsCompleteNodeResponse response = stsRunService.completeNode(
                owner,
                runId,
                1,
                new StsCompleteNodeRequest("encinst_elite", "Victory", 4, 61, 9, List.of("elite_enemy"), null)
        );

        JsonNode enchantments = null;
        for (JsonNode reward : json(response.pendingRewards())) {
            if ("CardChoice".equals(reward.path("type").asText())) {
                enchantments = reward.path("choices").path(0).path("enchantments");
            }
        }

        assertThat(enchantments).isNotNull();
        assertThat(enchantments).isNotEmpty();
        int levels = 0;
        for (JsonNode enchantment : enchantments) {
            assertThat(enchantment.path("enchantmentId").asText())
                    .isNotEqualTo("EliteEnchantment")
                    .isNotBlank();
            levels += enchantment.path("level").asInt();
        }
        assertThat(levels).as("the whole budget was spent").isEqualTo(3);
    }

    @Test
    void completeEliteNodePrefersNonBossRelicReward() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = encounterRun(runId, "Elite", "encinst_elite", List.of("elite_enemy"));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.rewardTables("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "gold": {"baseMinInclusive": 15, "baseMaxInclusive": 15, "eliteBonus": 10},
                  "cardChoices": {"choicesPerReward": 1, "eliteCardEnchant": {"enabled": false}},
                  "tokens": {"grantRules": []}
                }
                """));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "cards": [
                    {"id": "katana", "favoredCharacter": "Starting", "targetingMode": "Enemy"}
                  ]
                }
                """));
        when(gameDataService.relics("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "relics": [
                    {"id": "elite_relic", "backendGrantable": true, "rarity": "Rare"},
                    {"id": "boss_relic", "backendGrantable": true, "rarity": "Boss"}
                  ]
                }
                """));

        StsCompleteNodeResponse response = stsRunService.completeNode(
                owner,
                runId,
                1,
                new StsCompleteNodeRequest("encinst_elite", "Victory", 4, 61, 9, List.of("elite_enemy"), null)
        );

        String relicId = null;
        for (JsonNode reward : json(response.pendingRewards())) {
            if ("Relic".equals(reward.path("type").asText())) {
                relicId = reward.path("relicId").asText();
            }
        }

        assertThat(relicId).isEqualTo("elite_relic");
    }

    @Test
    void completeEliteNodeExcludesBossRelicFlaggedEntriesFromRelicRewards() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = encounterRun(runId, "Elite", "encinst_elite", List.of("elite_enemy"));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.rewardTables("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "gold": {"baseMinInclusive": 15, "baseMaxInclusive": 15, "eliteBonus": 10},
                  "cardChoices": {"choicesPerReward": 1, "eliteCardEnchant": {"enabled": false}},
                  "relics": {
                    "tables": [
                      {
                        "nodeTypes": ["Elite"],
                        "rarityWeights": {
                          "Rare": 100,
                          "Boss": 100
                        }
                      }
                    ]
                  },
                  "tokens": {"grantRules": []}
                }
                """));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "cards": [
                    {"id": "valid_a", "favoredCharacter": "MECA", "targetingMode": "Enemy", "tags": []}
                  ]
                }
                """));
        when(gameDataService.relics("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "relics": [
                    {"id": "normal_rare", "backendGrantable": true, "rarity": "Rare"},
                    {"id": "boss_marked", "backendGrantable": true, "rarity": "Rare", "bossRelic": true}
                  ]
                }
                """));

        StsCompleteNodeResponse response = stsRunService.completeNode(
                owner,
                runId,
                1,
                new StsCompleteNodeRequest("encinst_elite", "Victory", 4, 61, 9, List.of("elite_enemy"), null)
        );

        String relicId = null;
        for (JsonNode reward : json(response.pendingRewards())) {
            if ("Relic".equals(reward.path("type").asText())) {
                relicId = reward.path("relicId").asText();
            }
        }

        assertThat(relicId).isEqualTo("normal_rare");
    }

    @Test
    void completeBossNodeKeepsBossTierGuaranteeWhenBossRelicsAlreadyOwned() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = encounterRun(runId, "Boss", "encinst_boss", List.of("boss_enemy"));
        run.setRunInventory(objectMapper.readTree("""
                {
                  "gold": 0,
                  "deck": [],
                  "relics": [
                    {"instanceId": "relicinst_owned", "relicId": "boss_relic", "stage": 0}
                  ]
                }
                """));

        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.rewardTables("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "gold": {"baseMinInclusive": 15, "baseMaxInclusive": 15, "bossBonus": 30},
                  "cardChoices": {"choicesPerReward": 1, "eliteCardEnchant": {"enabled": false}},
                  "tokens": {"grantRules": []}
                }
                """));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "cards": [
                    {"id": "valid_a", "favoredCharacter": "MECA", "targetingMode": "Enemy", "tags": []}
                  ]
                }
                """));
        when(gameDataService.relics("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "relics": [
                    {"id": "elite_relic", "backendGrantable": true, "rarity": "Rare"},
                    {"id": "boss_relic", "backendGrantable": true, "rarity": "Boss"}
                  ]
                }
                """));

        StsCompleteNodeResponse response = stsRunService.completeNode(
                owner,
                runId,
                1,
                new StsCompleteNodeRequest("encinst_boss", "Victory", 3, 63, 7, List.of("boss_enemy"), null)
        );

        String relicId = null;
        for (JsonNode reward : json(response.pendingRewards())) {
            if ("Relic".equals(reward.path("type").asText())) {
                relicId = reward.path("relicId").asText();
            }
        }

        assertThat(relicId).isEqualTo("boss_relic");
    }

    @Test
    void completeNodeCardRewardsExcludeStartingImpossibleAndUnobtainableCards() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = encounterRun(runId, "Combat", "encinst_combat", List.of("enemy_1"));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.rewardTables("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "gold": {"baseMinInclusive": 15, "baseMaxInclusive": 15},
                  "cardChoices": {"choicesPerReward": 3, "eliteCardEnchant": {"enabled": false}},
                  "tokens": {"grantRules": []}
                }
                """));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "cards": [
                    {"id": "valid_a", "favoredCharacter": "MECA", "targetingMode": "Enemy", "tags": []},
                    {"id": "valid_b", "favoredCharacter": "Neutral", "targetingMode": "Enemy", "tags": []},
                    {"id": "valid_c", "favoredCharacter": "", "targetingMode": "Enemy", "tags": []},
                    {"id": "starting_only", "favoredCharacter": "Starting", "targetingMode": "Enemy", "tags": []},
                    {"id": "impossible_only", "favoredCharacter": "Impossible", "targetingMode": "Enemy", "tags": []},
                    {"id": "created_card", "favoredCharacter": "MECA", "targetingMode": "Enemy", "tags": ["Created"]},
                    {"id": "unobtainable_card", "favoredCharacter": "MECA", "targetingMode": "Enemy", "tags": ["Unobtainable"]}
                  ]
                }
                """));

        StsCompleteNodeResponse response = stsRunService.completeNode(
                owner,
                runId,
                1,
                new StsCompleteNodeRequest("encinst_combat", "Victory", 3, 65, 5, List.of("enemy_1"), null)
        );

        JsonNode pending = json(response.pendingRewards());
        JsonNode cardChoice = null;
        for (JsonNode reward : pending) {
            if ("CardChoice".equals(reward.path("type").asText())) {
                cardChoice = reward;
                break;
            }
        }

        assertThat(cardChoice).isNotNull();
        List<String> cardIds = cardChoice.path("choices").findValuesAsText("cardId");
        assertThat(cardIds).containsExactlyInAnyOrder("valid_a", "valid_b", "valid_c");
        assertThat(cardIds).doesNotContain("starting_only", "impossible_only", "created_card", "unobtainable_card");
    }

    @Test
    void completeNodeReplacesStaleUnclaimedPendingRewards() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = encounterRun(runId, "Combat", "encinst_combat", List.of("enemy_1"));
        run.setPendingRewards(objectMapper.readTree("""
                [
                  {"rewardId": "reward_old_relic", "type": "Relic", "relicId": "legacy_relic", "claimed": false}
                ]
                """));

        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.rewardTables("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "gold": {"baseMinInclusive": 15, "baseMaxInclusive": 15},
                  "cardChoices": {"choicesPerReward": 1, "eliteCardEnchant": {"enabled": false}},
                  "tokens": {"grantRules": []}
                }
                """));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "cards": [
                    {"id": "valid_a", "favoredCharacter": "MECA", "targetingMode": "Enemy", "tags": []}
                  ]
                }
                """));

        StsCompleteNodeResponse response = stsRunService.completeNode(
                owner,
                runId,
                1,
                new StsCompleteNodeRequest("encinst_combat", "Victory", 3, 65, 5, List.of("enemy_1"), null)
        );

        List<String> rewardIds = json(response.pendingRewards()).findValuesAsText("rewardId");
        assertThat(rewardIds).isNotEmpty();
        assertThat(rewardIds).doesNotContain("reward_old_relic");
    }

    @Test
    void completeBossNodeUsesRewardTableRarityWeightsForCardsAndRelics() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = encounterRun(runId, "Boss", "encinst_boss", List.of("boss_enemy"));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.rewardTables("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "gold": {"baseMinInclusive": 15, "baseMaxInclusive": 15, "bossBonus": 30},
                  "cardChoices": {
                    "choicesPerReward": 2,
                    "eliteCardEnchant": {"enabled": false},
                    "tables": [
                      {
                        "nodeTypes": ["Boss"],
                        "rarityWeights": {
                          "Common": 100,
                          "Special": 0
                        }
                      }
                    ]
                  },
                  "relics": {
                    "tables": [
                      {
                        "nodeTypes": ["Boss"],
                        "rarityWeights": {
                          "Boss": 100,
                          "Rare": 0,
                          "Special": 0
                        }
                      }
                    ]
                  },
                  "tokens": {"grantRules": []}
                }
                """));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "cards": [
                    {"id": "boss_common", "favoredCharacter": "MECA", "targetingMode": "Enemy", "rarity": "Common", "tags": []},
                    {"id": "boss_special", "favoredCharacter": "MECA", "targetingMode": "Enemy", "rarity": "Special", "tags": []}
                  ]
                }
                """));
        when(gameDataService.relics("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "relics": [
                    {"id": "normal_relic", "backendGrantable": true, "rarity": "Rare"},
                    {"id": "boss_relic", "backendGrantable": true, "rarity": "Boss"}
                  ]
                }
                """));

        StsCompleteNodeResponse response = stsRunService.completeNode(
                owner,
                runId,
                1,
                new StsCompleteNodeRequest("encinst_boss", "Victory", 3, 63, 7, List.of("boss_enemy"), null)
        );

        JsonNode pending = json(response.pendingRewards());
        JsonNode cardChoice = null;
        String relicId = null;
        for (JsonNode reward : pending) {
            if ("CardChoice".equals(reward.path("type").asText())) {
                cardChoice = reward;
            }
            if ("Relic".equals(reward.path("type").asText())) {
                relicId = reward.path("relicId").asText();
            }
        }

        assertThat(cardChoice).isNotNull();
        List<String> cardIds = cardChoice.path("choices").findValuesAsText("cardId");
        assertThat(cardIds).contains("boss_common");
        assertThat(cardIds).doesNotContain("boss_special");
        assertThat(relicId).isEqualTo("boss_relic");
    }

    /**
     * Une table de récompenses qui ne nomme pas le boss ne doit pas le laisser sans pondération.
     *
     * <p>Le boss retombait alors sur un poids de 1 par carte : la table était bien lue pour un
     * combat ordinaire, et ignorée juste après le boss — précisément là où les raretés comptent
     * le plus.
     */
    @Test
    void completeBossNodeFallsBackToCombatRarityWeightsWhenNoBossTableIsConfigured() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = encounterRun(runId, "Boss", "encinst_boss", List.of("boss_enemy"));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.rewardTables("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "gold": {"baseMinInclusive": 15, "baseMaxInclusive": 15, "bossBonus": 30},
                  "cardChoices": {
                    "choicesPerReward": 2,
                    "eliteCardEnchant": {"enabled": false},
                    "tables": [
                      {
                        "nodeTypes": ["Combat", "Elite"],
                        "rarityWeights": {
                          "Common": 100,
                          "Special": 0
                        }
                      }
                    ]
                  },
                  "relics": {"tables": []},
                  "tokens": {"grantRules": []}
                }
                """));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "cards": [
                    {"id": "boss_common", "favoredCharacter": "MECA", "targetingMode": "Enemy", "rarity": "Common", "tags": []},
                    {"id": "boss_special", "favoredCharacter": "MECA", "targetingMode": "Enemy", "rarity": "Special", "tags": []}
                  ]
                }
                """));
        when(gameDataService.relics("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {"relics": []}
                """));

        StsCompleteNodeResponse response = stsRunService.completeNode(
                owner,
                runId,
                1,
                new StsCompleteNodeRequest("encinst_boss", "Victory", 3, 63, 7, List.of("boss_enemy"), null)
        );

        JsonNode cardChoice = null;
        for (JsonNode reward : json(response.pendingRewards())) {
            if ("CardChoice".equals(reward.path("type").asText())) {
                cardChoice = reward;
            }
        }

        assertThat(cardChoice).isNotNull();
        List<String> cardIds = cardChoice.path("choices").findValuesAsText("cardId");
        assertThat(cardIds).contains("boss_common");
        assertThat(cardIds).doesNotContain("boss_special");
    }

    @Test
    void completeNodeFallsBackWhenConfiguredCardRarityKeysDoNotMatchCatalog() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = encounterRun(runId, "Combat", "encinst_combat", List.of("enemy_1"));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.rewardTables("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "gold": {"baseMinInclusive": 15, "baseMaxInclusive": 15},
                  "cardChoices": {
                    "choicesPerReward": 2,
                    "eliteCardEnchant": {"enabled": false},
                    "tables": [
                      {
                        "nodeTypes": ["Combat"],
                        "rarityWeights": {
                          "LegendaryOnly": 100
                        }
                      }
                    ]
                  },
                  "tokens": {"grantRules": []}
                }
                """));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "cards": [
                    {"id": "valid_a", "favoredCharacter": "MECA", "targetingMode": "Enemy", "rarity": "Common", "tags": []},
                    {"id": "valid_b", "favoredCharacter": "MECA", "targetingMode": "Enemy", "rarity": "Uncommon", "tags": []}
                  ]
                }
                """));

        StsCompleteNodeResponse response = stsRunService.completeNode(
                owner,
                runId,
                1,
                new StsCompleteNodeRequest("encinst_combat", "Victory", 3, 65, 5, List.of("enemy_1"), null)
        );

        JsonNode cardChoice = null;
        for (JsonNode reward : json(response.pendingRewards())) {
            if ("CardChoice".equals(reward.path("type").asText())) {
                cardChoice = reward;
            }
        }

        assertThat(cardChoice).isNotNull();
        List<String> cardIds = cardChoice.path("choices").findValuesAsText("cardId");
        assertThat(cardIds).isNotEmpty();
    }

    @Test
    void completeBossNodeFallsBackToAvailableRelicWhenNoBossTierCanBeResolved() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = encounterRun(runId, "Boss", "encinst_boss", List.of("boss_enemy"));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.rewardTables("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "gold": {"baseMinInclusive": 15, "baseMaxInclusive": 15, "bossBonus": 30},
                  "cardChoices": {"choicesPerReward": 1, "eliteCardEnchant": {"enabled": false}},
                  "relics": {
                    "tables": [
                      {
                        "nodeTypes": ["Boss"],
                        "rarityWeights": {
                          "Boss": 100,
                          "Rare": 0
                        }
                      }
                    ]
                  },
                  "tokens": {"grantRules": []}
                }
                """));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "cards": [
                    {"id": "valid_a", "favoredCharacter": "MECA", "targetingMode": "Enemy", "tags": []}
                  ]
                }
                """));
        when(gameDataService.relics("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "relics": [
                    {"id": "relic_1", "backendGrantable": true, "rarity": "Rare"},
                    {"id": "relic_2", "backendGrantable": true, "rarity": "Rare"}
                  ]
                }
                """));

        StsCompleteNodeResponse response = stsRunService.completeNode(
                owner,
                runId,
                1,
                new StsCompleteNodeRequest("encinst_boss", "Victory", 3, 63, 7, List.of("boss_enemy"), null)
        );

        String relicId = null;
        for (JsonNode reward : json(response.pendingRewards())) {
            if ("Relic".equals(reward.path("type").asText())) {
                relicId = reward.path("relicId").asText();
            }
        }

        assertThat(relicId).isIn("relic_1", "relic_2");
    }

    @Test
    void completeEliteNodeStillGrantsRelicWhenAllGrantableRelicsAreAlreadyOwned() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = encounterRun(runId, "Elite", "encinst_elite", List.of("elite_enemy"));
        run.setRunInventory(objectMapper.readTree("""
                {
                  "gold": 0,
                  "deck": [],
                  "relics": [
                    {"instanceId": "relicinst_1", "relicId": "elite_relic", "stage": 0}
                  ]
                }
                """));

        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.rewardTables("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "gold": {"baseMinInclusive": 15, "baseMaxInclusive": 15, "eliteBonus": 10},
                  "cardChoices": {"choicesPerReward": 1, "eliteCardEnchant": {"enabled": false}},
                  "tokens": {"grantRules": []}
                }
                """));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "cards": [
                    {"id": "valid_a", "favoredCharacter": "MECA", "targetingMode": "Enemy", "tags": []}
                  ]
                }
                """));
        when(gameDataService.relics("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "relics": [
                    {"id": "elite_relic", "backendGrantable": true, "rarity": "Rare"}
                  ]
                }
                """));

        StsCompleteNodeResponse response = stsRunService.completeNode(
                owner,
                runId,
                1,
                new StsCompleteNodeRequest("encinst_elite", "Victory", 4, 61, 9, List.of("elite_enemy"), null)
        );

        String relicId = null;
        for (JsonNode reward : json(response.pendingRewards())) {
            if ("Relic".equals(reward.path("type").asText())) {
                relicId = reward.path("relicId").asText();
            }
        }

        assertThat(relicId).isEqualTo("elite_relic");
    }

    @Test
    void completeBossNodeCardFallbackPrefersHighestRarityWhenConfiguredKeysDoNotMatch() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = encounterRun(runId, "Boss", "encinst_boss", List.of("boss_enemy"));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.rewardTables("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "gold": {"baseMinInclusive": 15, "baseMaxInclusive": 15, "bossBonus": 30},
                  "cardChoices": {
                    "choicesPerReward": 3,
                    "eliteCardEnchant": {"enabled": false},
                    "tables": [
                      {
                        "nodeTypes": ["Boss"],
                        "rarityWeights": {
                          "OnlyMyCustomKey": 100
                        }
                      }
                    ]
                  },
                  "tokens": {"grantRules": []}
                }
                """));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "cards": [
                    {"id": "boss_common", "favoredCharacter": "MECA", "targetingMode": "Enemy", "rarity": "Common", "tags": []},
                    {"id": "boss_rare", "favoredCharacter": "MECA", "targetingMode": "Enemy", "rarity": "Rare", "tags": []},
                    {"id": "boss_legendary", "favoredCharacter": "MECA", "targetingMode": "Enemy", "rarity": "Legendary", "tags": []}
                  ]
                }
                """));
        when(gameDataService.relics("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "relics": [
                    {"id": "boss_relic", "backendGrantable": true, "rarity": "Boss"}
                  ]
                }
                """));

        StsCompleteNodeResponse response = stsRunService.completeNode(
                owner,
                runId,
                1,
                new StsCompleteNodeRequest("encinst_boss", "Victory", 3, 63, 7, List.of("boss_enemy"), null)
        );

        JsonNode cardChoice = null;
        for (JsonNode reward : json(response.pendingRewards())) {
            if ("CardChoice".equals(reward.path("type").asText())) {
                cardChoice = reward;
            }
        }

        assertThat(cardChoice).isNotNull();
        List<String> cardIds = cardChoice.path("choices").findValuesAsText("cardId");
        assertThat(cardIds).isNotEmpty();
        assertThat(cardIds).containsOnly("boss_legendary");
    }

    @Test
    void completeBossNodeTreatsLegendaryRelicAsBossTierForTableWeights() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = encounterRun(runId, "Boss", "encinst_boss", List.of("boss_enemy"));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.rewardTables("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "gold": {"baseMinInclusive": 15, "baseMaxInclusive": 15, "bossBonus": 30},
                  "cardChoices": {"choicesPerReward": 1, "eliteCardEnchant": {"enabled": false}},
                  "relics": {
                    "tables": [
                      {
                        "nodeTypes": ["Boss"],
                        "rarityWeights": {
                          "Boss": 100,
                          "Rare": 0
                        }
                      }
                    ]
                  },
                  "tokens": {"grantRules": []}
                }
                """));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "cards": [
                    {"id": "valid_a", "favoredCharacter": "MECA", "targetingMode": "Enemy", "tags": []}
                  ]
                }
                """));
        when(gameDataService.relics("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "relics": [
                    {"id": "normal_relic", "backendGrantable": true, "rarity": "Rare"},
                    {"id": "legendary_relic", "backendGrantable": true, "rarity": "Legendary"}
                  ]
                }
                """));

        StsCompleteNodeResponse response = stsRunService.completeNode(
                owner,
                runId,
                1,
                new StsCompleteNodeRequest("encinst_boss", "Victory", 3, 63, 7, List.of("boss_enemy"), null)
        );

        String relicId = null;
        for (JsonNode reward : json(response.pendingRewards())) {
            if ("Relic".equals(reward.path("type").asText())) {
                relicId = reward.path("relicId").asText();
            }
        }

        assertThat(relicId).isEqualTo("legendary_relic");
    }

    @Test
    void completeBossNodeUsesBossRelicBooleanForBossWeightSelection() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = encounterRun(runId, "Boss", "encinst_boss", List.of("boss_enemy"));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.rewardTables("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "gold": {"baseMinInclusive": 15, "baseMaxInclusive": 15, "bossBonus": 30},
                  "cardChoices": {"choicesPerReward": 1, "eliteCardEnchant": {"enabled": false}},
                  "relics": {
                    "tables": [
                      {
                        "nodeTypes": ["Boss"],
                        "rarityWeights": {
                          "Boss": 100,
                          "Rare": 0
                        }
                      }
                    ]
                  },
                  "tokens": {"grantRules": []}
                }
                """));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "cards": [
                    {"id": "valid_a", "favoredCharacter": "MECA", "targetingMode": "Enemy", "tags": []}
                  ]
                }
                """));
        when(gameDataService.relics("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "relics": [
                    {"id": "normal_relic", "backendGrantable": true, "rarity": "Rare"},
                    {"id": "flagged_boss_relic", "backendGrantable": true, "rarity": "Rare", "bossRelic": true}
                  ]
                }
                """));

        StsCompleteNodeResponse response = stsRunService.completeNode(
                owner,
                runId,
                1,
                new StsCompleteNodeRequest("encinst_boss", "Victory", 3, 63, 7, List.of("boss_enemy"), null)
        );

        String relicId = null;
        for (JsonNode reward : json(response.pendingRewards())) {
            if ("Relic".equals(reward.path("type").asText())) {
                relicId = reward.path("relicId").asText();
            }
        }

        assertThat(relicId).isEqualTo("flagged_boss_relic");
    }

    @Test
    void completeBossNodePrefersBossRelicPoolEvenWhenRareWeightIsPositive() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = encounterRun(runId, "Boss", "encinst_boss", List.of("boss_enemy"));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.rewardTables("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "gold": {"baseMinInclusive": 15, "baseMaxInclusive": 15, "bossBonus": 30},
                  "cardChoices": {"choicesPerReward": 1, "eliteCardEnchant": {"enabled": false}},
                  "relics": {
                    "tables": [
                      {
                        "nodeTypes": ["Boss"],
                        "rarityWeights": {
                          "Boss": 50,
                          "Rare": 50
                        }
                      }
                    ]
                  },
                  "tokens": {"grantRules": []}
                }
                """));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "cards": [
                    {"id": "valid_a", "favoredCharacter": "MECA", "targetingMode": "Enemy", "tags": []}
                  ]
                }
                """));
        when(gameDataService.relics("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "relics": [
                    {"id": "normal_rare", "backendGrantable": true, "rarity": "Rare"},
                    {"id": "boss_marked", "backendGrantable": true, "rarity": "Rare", "bossRelic": true}
                  ]
                }
                """));

        StsCompleteNodeResponse response = stsRunService.completeNode(
                owner,
                runId,
                1,
                new StsCompleteNodeRequest("encinst_boss", "Victory", 3, 63, 7, List.of("boss_enemy"), null)
        );

        String relicId = null;
        for (JsonNode reward : json(response.pendingRewards())) {
            if ("Relic".equals(reward.path("type").asText())) {
                relicId = reward.path("relicId").asText();
            }
        }

        assertThat(relicId).isEqualTo("boss_marked");
    }

    @Test
    void chooseEventOptionAppliesEntriesAndCompletesEventNode() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = run(runId);
        run.setEnteredNodeId(1);
        run.setActiveEvent(objectMapper.readTree("""
                {
                  "eventInstanceId": "evtinst_1",
                  "nodeId": 1,
                  "eventId": "event_student",
                  "options": [
                    {
                      "optionId": "option_0",
                      "completionMessage": "Done",
                      "entries": [
                        {"type": "Damage", "value": 7, "id": ""},
                        {"type": "Heal", "value": 2, "id": ""},
                        {"type": "GoldReward", "value": 12, "id": ""},
                        {"type": "MaxHpGain", "value": 5, "id": ""},
                        {"type": "CardReward", "value": 1, "id": ""}
                      ]
                    }
                  ]
                }
                """));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.rewardTables("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "cardChoices": {
                    "choicesPerReward": 2,
                    "eliteCardEnchant": {"enabled": false}
                  }
                }
                """));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "cards": [
                    {"id": "katana", "favoredCharacter": "Starting"},
                    {"id": "robot_autoguide", "favoredCharacter": "MECA"}
                  ]
                }
                """));

        StsChooseEventOptionResponse response = stsRunService.chooseEventOption(
                owner,
                runId,
                "evtinst_1",
                new StsChooseEventOptionRequest("option_0", null)
        );

        assertThat(response.accepted()).isTrue();
        assertThat(response.eventCompleted()).isTrue();
        assertThat(response.completionMessage()).isEqualTo("Done");
        assertThat(json(response.player()).path("maxHp").asInt()).isEqualTo(75);
        assertThat(json(response.player()).path("currentHp").asInt()).isEqualTo(70);
        assertThat(json(response.runInventoryPatch()).path("goldDelta").asInt()).isEqualTo(12);
        assertThat(json(response.pendingRewards())).hasSize(1);
        assertThat(run.getRunInventory().path("gold").asInt()).isEqualTo(12);
        assertThat(run.getCurrentNodeId()).isEqualTo(1);
        assertThat(run.getEnteredNodeId()).isNull();
        assertThat(run.getActiveEvent()).isNull();
        assertThat(json(stsRunService.toDto(run).map()).path("nodes").get(1).path("completed").asBoolean()).isTrue();
    }

    @Test
    void chooseEventOptionAddsCardsAndIgnoresNoOpEntries() throws Exception {
        // Deux des dix événements livrés utilisent des types que le serveur ne traitait
        // pas, et sa branche par défaut lève une 400 : « Turbulences » (AddCard) aurait
        // bloqué la run, le joueur ne pouvant plus sortir du nœud.
        UUID runId = UUID.randomUUID();
        StsRun run = run(runId);
        run.setEnteredNodeId(1);
        run.setActiveEvent(objectMapper.readTree("""
                {
                  "eventInstanceId": "evtinst_1",
                  "nodeId": 1,
                  "eventId": "event_turbulences",
                  "options": [
                    {
                      "optionId": "option_0",
                      "completionMessage": "Done",
                      "entries": [
                        {"type": "None", "value": 0, "id": ""},
                        {"type": "AddCard", "value": 2, "id": "katana"}
                      ]
                    }
                  ]
                }
                """));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "cards": [
                    {"id": "katana", "favoredCharacter": "Starting", "targetingMode": "Enemy"}
                  ]
                }
                """));

        int deckBefore = run.getRunInventory().path("deck").size();

        StsChooseEventOptionResponse response = stsRunService.chooseEventOption(
                owner,
                runId,
                "evtinst_1",
                new StsChooseEventOptionRequest("option_0", null)
        );

        assertThat(response.accepted()).isTrue();
        assertThat(run.getRunInventory().path("deck").size()).isEqualTo(deckBefore + 2);
        assertThat(json(response.runInventoryPatch()).path("addedCards")).hasSize(2);
        for (JsonNode added : json(response.runInventoryPatch()).path("addedCards")) {
            assertThat(added.path("cardId").asText()).isEqualTo("katana");
            assertThat(added.path("instanceId").asText()).startsWith("cardinst_");
        }
    }

    @Test
    void chooseEventOptionReplacesOptionsWithoutClosingTheEvent() throws Exception {
        // « Ascension de la colline » propose un choix qui en ouvre d'autres au lieu de
        // clore le nœud. Sans ce cas, l'événement était rejeté et la run bloquée.
        UUID runId = UUID.randomUUID();
        StsRun run = run(runId);
        run.setEnteredNodeId(1);
        run.setActiveEvent(objectMapper.readTree("""
                {
                  "eventInstanceId": "evtinst_1",
                  "nodeId": 1,
                  "eventId": "event_colline",
                  "options": [
                    {
                      "optionId": "monter",
                      "entries": [
                        {"type": "Damage", "value": 3, "id": ""},
                        {"type": "ReplaceOptions", "value": 0, "id": "", "targetIds": [],
                         "replacementOptions": [
                           {"optionId": "continuer", "entries": [{"type": "GoldReward", "value": 5, "id": ""}]},
                           {"optionId": "redescendre", "entries": []}
                         ]}
                      ]
                    },
                    {"optionId": "passer", "entries": []}
                  ]
                }
                """));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));

        StsChooseEventOptionResponse response = stsRunService.chooseEventOption(
                owner,
                runId,
                "evtinst_1",
                new StsChooseEventOptionRequest("monter", null)
        );

        assertThat(response.accepted()).isTrue();
        // L'événement continue : ni nœud terminé, ni activeEvent effacé.
        assertThat(response.eventCompleted()).isFalse();
        assertThat(run.getEnteredNodeId()).isEqualTo(1);
        assertThat(run.getActiveEvent()).isNotNull();
        assertThat(json(stsRunService.toDto(run).map()).path("nodes").get(1).path("completed").asBoolean()).isFalse();

        // Les dégâts de l'entrée précédente ont bien été appliqués.
        assertThat(run.getPlayerState().path("currentHp").asInt()).isEqualTo(67);

        // L'option choisie a cédé la place à ses remplaçantes, l'autre est intacte.
        List<String> optionIds = new ArrayList<>();
        run.getActiveEvent().path("options").forEach(o -> optionIds.add(o.path("optionId").asText()));
        assertThat(optionIds).containsExactly("continuer", "redescendre", "passer");

        // Et le client reçoit l'événement à jour pour réafficher ses options.
        assertThat(json(response.activeEvent()).path("options")).hasSize(3);
    }

    @Test
    void chooseEventOptionNamesReplacementsTheCatalogLeftUnnamed() throws Exception {
        // Le catalogue exporté par Unity ne nomme aucune option : « id » y est vide
        // partout, et les remplaçantes arrivaient donc dans l'événement sans optionId.
        // Le client les sautait à l'affichage et le serveur refusait le choix suivant :
        // « Ascension de la colline » restait bloquée sur son premier palier.
        UUID runId = UUID.randomUUID();
        StsRun run = run(runId);
        run.setEnteredNodeId(1);
        run.setActiveEvent(objectMapper.readTree("""
                {
                  "eventInstanceId": "evtinst_1",
                  "nodeId": 1,
                  "eventId": "event_colline",
                  "options": [
                    {
                      "optionId": "option_0",
                      "entries": [
                        {"type": "ReplaceOptions", "value": 0, "id": "", "targetIds": [],
                         "replacementOptions": [
                           {"id": "", "text": "Continuer à grimper",
                            "completionMessage": "Vous approchez de la fin !",
                            "entries": [{"type": "GoldReward", "value": 5, "id": ""}]},
                           {"id": "", "text": "Redescendre", "entries": []}
                         ]}
                      ]
                    },
                    {"optionId": "option_1", "entries": []}
                  ]
                }
                """));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));

        StsChooseEventOptionResponse response = stsRunService.chooseEventOption(
                owner, runId, "evtinst_1", new StsChooseEventOptionRequest("option_0", null));

        assertThat(response.eventCompleted()).isFalse();

        List<String> optionIds = new ArrayList<>();
        run.getActiveEvent().path("options").forEach(o -> optionIds.add(o.path("optionId").asText()));
        assertThat(optionIds).containsExactly("option_0.r0", "option_0.r1", "option_1");
        assertThat(json(response.activeEvent()).path("options").get(0).path("text").asText())
                .isEqualTo("Continuer à grimper");

        // Et le palier suivant se joue : c'est ce qui manquait.
        StsChooseEventOptionResponse second = stsRunService.chooseEventOption(
                owner, runId, "evtinst_1", new StsChooseEventOptionRequest("option_0.r0", null));

        assertThat(second.accepted()).isTrue();
        assertThat(second.eventCompleted()).isTrue();
        assertThat(second.completionMessage()).isEqualTo("Vous approchez de la fin !");
        assertThat(run.getRunInventory().path("gold").asInt()).isEqualTo(5);
    }

    @Test
    void chooseEventOptionUpgradesTheSelectedCard() throws Exception {
        // Le feu de camp fait exactement ce geste : enchanter une carte choisie. La
        // chaîne construite ici lui servira aussi, avec un nombre de charges différent.
        UUID runId = UUID.randomUUID();
        StsRun run = run(runId);
        run.setRunInventory(objectMapper.readTree("""
                {"gold":0,"relics":[],"deck":[
                  {"instanceId":"cardinst_a","cardId":"katana","targetingMode":"Enemy","enchantments":[]}
                ]}
                """));
        run.setEnteredNodeId(1);
        run.setActiveEvent(objectMapper.readTree("""
                {
                  "eventInstanceId": "evtinst_1",
                  "nodeId": 1,
                  "eventId": "event_forge",
                  "options": [
                    {"optionId": "forger", "entries": [{"type": "UpgradeCard", "value": 1, "id": ""}]}
                  ]
                }
                """));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "cards": [
                    {"id": "katana", "cardName": "Katana", "type": "Attaque", "rarity": "Commune",
                     "targetingMode": "Enemy", "cost": 1, "favoredCharacter": "Starting",
                     "effects": [{"type": "Damage", "value": 6}]}
                  ]
                }
                """));

        StsChooseEventOptionResponse response = stsRunService.chooseEventOption(
                owner,
                runId,
                "evtinst_1",
                new StsChooseEventOptionRequest("forger", List.of("cardinst_a"))
        );

        assertThat(response.accepted()).isTrue();
        JsonNode card = run.getRunInventory().path("deck").get(0);
        assertThat(card.path("instanceId").asText()).isEqualTo("cardinst_a");
        // La carte repart enchantée, et c'est le serveur qui a tiré.
        assertThat(card.path("enchantments")).isNotEmpty();
        assertThat(card.path("enchantments").get(0).path("level").asInt()).isPositive();
        assertThat(card.path("enchantments").get(0).path("enchantmentId").asText()).isNotBlank();
    }

    @Test
    void chooseEventOptionRequiresExistingOption() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = run(runId);
        run.setEnteredNodeId(1);
        run.setActiveEvent(objectMapper.readTree("""
                {
                  "eventInstanceId": "evtinst_1",
                  "nodeId": 1,
                  "eventId": "event_student",
                  "options": [
                    {"optionId": "option_0", "entries": []}
                  ]
                }
                """));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> stsRunService.chooseEventOption(
                owner,
                runId,
                "evtinst_1",
                new StsChooseEventOptionRequest("missing", null)
        )).hasMessageContaining("Option");
    }

    @Test
    void resetRunAbandonsActiveRunAndClearsActiveState() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = run(runId);
        run.setEnteredNodeId(1);
        run.setActiveEvent(objectMapper.readTree("{\"id\":\"event_shrine\"}"));
        run.setActiveEncounter(objectMapper.readTree("{\"encounterInstanceId\":\"encinst_1\"}"));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));

        stsRunService.resetRun(owner, runId);

        assertThat(run.getStatus()).isEqualTo(StsRunStatus.Abandoned);
        assertThat(run.getEnteredNodeId()).isNull();
        assertThat(run.getActiveEvent()).isNull();
        assertThat(run.getActiveEncounter()).isNull();
    }

    @Test
    void claimRewardReassignsJsonStateSoHibernatePersistsTheClaim() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = run(runId);
        run.setRunInventory(objectMapper.readTree("{\"gold\":4,\"deck\":[],\"relics\":[]}"));
        run.setPendingRewards(objectMapper.readTree("""
                [{"rewardId":"reward_gold","type":"Gold","amount":10,"claimed":false}]
                """));
        JsonNode originalInventory = run.getRunInventory();
        JsonNode originalRewards = run.getPendingRewards();
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));

        var response = stsRunService.claimReward(
                owner,
                runId,
                "reward_gold",
                new StsClaimRewardRequest(null)
        );

        assertThat(json(response.pendingRewards())).isEmpty();
        assertThat(run.getRunInventory().path("gold").asInt()).isEqualTo(14);
        assertThat(run.getRunInventory()).isNotSameAs(originalInventory);
        assertThat(run.getPendingRewards()).isNotSameAs(originalRewards);
    }

    @Test
    void continueAfterRetreatAdvancesOnlyFromACompletedBoss() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = run(runId);
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> stsRunService.continueAfterRetreat(owner, runId, 0))
                .hasMessageContaining("boss");
    }

    @Test
    void continueAfterRetreatRetryDoesNotAdvanceTwice() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = completedBossRun(runId);
        run.setAct(2);
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.mapGeneration("2026-07-10.1")).thenReturn(minimalMapConfig());

        StsRunStateDto first = stsRunService.continueAfterRetreat(owner, runId, 2);
        StsRunStateDto retry = stsRunService.continueAfterRetreat(owner, runId, 2);

        assertThat(first.act()).isEqualTo(3);
        assertThat(retry.act()).isEqualTo(3);
        assertThat(run.getAct()).isEqualTo(3);
    }

    @Test
    void continueAfterRetreatHealsThePlayerToFull() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = completedBossRun(runId);
        run.setAct(1);
        run.setPlayerState(objectMapper.readTree("{\"maxHp\":80,\"currentHp\":12}"));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.mapGeneration("2026-07-10.1")).thenReturn(minimalMapConfig());

        stsRunService.continueAfterRetreat(owner, runId, 1);

        assertThat(run.getPlayerState().path("currentHp").asInt()).isEqualTo(80);
        assertThat(run.getPlayerState().path("maxHp").asInt()).isEqualTo(80);
    }

    /** Le soin ne s'applique qu'en franchissant l'acte : un refus laisse les PV tels quels. */
    @Test
    void continueAfterRetreatLeavesHpAloneWhenItRefusesToAdvance() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = run(runId);
        run.setPlayerState(objectMapper.readTree("{\"maxHp\":80,\"currentHp\":12}"));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> stsRunService.continueAfterRetreat(owner, runId, 0))
                .hasMessageContaining("boss");

        assertThat(run.getPlayerState().path("currentHp").asInt()).isEqualTo(12);
    }

    @Test
    void retireRunCalculatesAccumulatedScoreAndCreditsTokensAfterBoss() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = run(runId);
        run.setAct(2);
        run.setCurrentFloor(4);
        run.setCurrentNodeId(4);
        run.setPlayerState(objectMapper.readTree("{\"maxHp\":80,\"currentHp\":40}"));
        run.setRunInventory(objectMapper.readTree("""
                {"gold":50,"deck":[{},{},{}],"relics":[{},{}]}
                """));
        run.setMapState(objectMapper.readTree("""
                {
                  "nodes": [
                    {"id":0,"type":"Start","visited":true,"completed":true},
                    {"id":1,"type":"Combat","visited":true,"completed":true},
                    {"id":2,"type":"Elite","visited":true,"completed":true},
                    {"id":3,"type":"Event","visited":true,"completed":true},
                    {"id":4,"type":"Boss","visited":true,"completed":true}
                  ]
                }
                """));
        owner.setTokens(7L);
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(userRepository.findWithLockByKeycloakSub(owner.getKeycloakSub())).thenReturn(Optional.of(owner));
        when(gameDataService.runScoreRewards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "score": {
                    "visitedNode": 15,
                    "combatVictory": 25,
                    "eliteVictory": 60,
                    "eventVisited": 20,
                    "actReached": 120,
                    "relicOwned": 40,
                    "deckCard": 5,
                    "goldOwned": 1,
                    "remainingHpPercent": 2
                  },
                  "tokens": {"scorePerToken":20,"minimumReward":10,"rounding":"NEAREST"}
                }
                """));

        StsRetireRunResponse response = stsRunService.retireRun(owner, runId);

        assertThat(response.score()).isEqualTo(665);
        assertThat(response.tokensGranted()).isEqualTo(33);
        assertThat(response.tokenBalance()).isEqualTo(40);
        assertThat(run.getStatus()).isEqualTo(StsRunStatus.Retired);
        assertThat(run.getRetirementScore()).isEqualTo(665L);
        assertThat(run.getRetirementTokens()).isEqualTo(33L);
    }

    @Test
    void retireRunRejectsRetirementWhileANodeIsActive() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = completedBossRun(runId);
        run.setEnteredNodeId(4);
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> stsRunService.retireRun(owner, runId))
                .hasMessageContaining("pendant un node");
    }

    @Test
    void retireRunReturnsStoredResolutionWithoutCreditingTokensTwice() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = completedBossRun(runId);
        owner.setTokens(5L);
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(userRepository.findWithLockByKeycloakSub(owner.getKeycloakSub())).thenReturn(Optional.of(owner));
        when(gameDataService.runScoreRewards("2026-07-10.1")).thenReturn(retirementRules());

        StsRetireRunResponse first = stsRunService.retireRun(owner, runId);
        StsRetireRunResponse retry = stsRunService.retireRun(owner, runId);

        assertThat(retry.score()).isEqualTo(first.score());
        assertThat(first.tokensGranted()).isPositive();
        assertThat(retry.tokensGranted()).isZero();
        assertThat(retry.tokenBalance()).isEqualTo(first.tokenBalance());
        verify(userRepository, times(2)).findWithLockByKeycloakSub(owner.getKeycloakSub());
    }

      @Test
      void retreatPreviewReturnsZeroAdditionalTokensWhenRunAlreadyRetired() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = completedBossRun(runId);
        run.setStatus(StsRunStatus.Retired);
        run.setRetirementScore(665L);
        run.setRetirementTokens(33L);

        owner.setTokens(40L);
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(userRepository.findWithLockByKeycloakSub(owner.getKeycloakSub())).thenReturn(Optional.of(owner));
        when(gameDataService.runScoreRewards("2026-07-10.1")).thenReturn(retirementRules());

        StsRetreatPreviewResponse preview = stsRunService.retreatPreview(owner, runId);

        assertThat(preview.status()).isEqualTo(StsRunStatus.Retired);
        assertThat(preview.score()).isEqualTo(665L);
        assertThat(preview.tokensPreview()).isZero();
        assertThat(preview.projectedTokenBalance()).isEqualTo(40L);
      }

    private StsRun completedBossRun(UUID runId) throws Exception {
        StsRun run = run(runId);
        run.setCurrentNodeId(4);
        run.setMapState(objectMapper.readTree("""
                {"nodes":[{"id":4,"type":"Boss","visited":true,"completed":true}]}
                """));
        return run;
    }

    /**
     * Une carte {@code Secret} ne tombe pas tant que le compte n'a pas ouvert le pool.
     *
     * <p>Le silence est le point : rien ne dit au joueur qu'il manque une carte, et la carte qui
     * ouvre le pool reste, elle, parfaitement offrable -- c'est la seule facon d'y arriver.
     */
    @Test
    void cardRewardsHideSecretCardsUntilTheAccountHasUnlockedThem() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = encounterRun(runId, "Combat", "encinst_combat", List.of("enemy_1"));
        givenSecretCardCatalog(runId, run);

        StsCompleteNodeResponse response = stsRunService.completeNode(
                owner, runId, 1,
                new StsCompleteNodeRequest("encinst_combat", "Victory", 3, 63, 7, List.of("enemy_1"), null));

        assertThat(cardChoiceIds(response)).containsExactlyInAnyOrder("ordinaire", "sesame");
    }

    /** Une fois le pool ouvert, la carte secrete tombe comme n'importe quelle autre. */
    @Test
    void cardRewardsOfferSecretCardsOnceTheAccountHasUnlockedThem() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = encounterRun(runId, "Combat", "encinst_combat", List.of("enemy_1"));
        owner.setStsSecretCardsUnlockedAt(Instant.parse("2026-07-10T10:00:00Z"));
        givenSecretCardCatalog(runId, run);

        StsCompleteNodeResponse response = stsRunService.completeNode(
                owner, runId, 1,
                new StsCompleteNodeRequest("encinst_combat", "Victory", 3, 63, 7, List.of("enemy_1"), null));

        assertThat(cardChoiceIds(response)).containsExactlyInAnyOrder("ordinaire", "secrete", "sesame");
    }

    /**
     * L'Acte 3 lit la table de l'acte 2 quand l'acte 3 n'en a pas.
     *
     * <p>C'est ce qui permet d'ecrire une seule table de fin de partie plutot que de recopier la
     * meme dans chaque acte suivant.
     */
    @Test
    void cardRewardWeightsFallBackToTheLatestActTableAlreadyReached() throws Exception {
        assertThat(rewardedCardIdForActIndex(2)).isEqualTo("legendaire");
    }

    /**
     * Un acte anterieur a toute declinaison lit la table non declinee.
     *
     * <p>Le repli ne remonte jamais : heriter d'une table ecrite pour plus tard donnerait au
     * premier acte les raretes de la fin de partie.
     */
    @Test
    void cardRewardWeightsBeforeAnyAuthoredActReadTheUndeclinedTable() throws Exception {
        assertThat(rewardedCardIdForActIndex(0)).isEqualTo("commune");
    }

    /** L'acte de depart vient de l'enemy pool, et une nouvelle run y commence. */
    @Test
    void startRunBeginsOnTheActTheEnemyPoolConfigures() throws Exception {
        when(userRepository.findWithLockByKeycloakSub(owner.getKeycloakSub())).thenReturn(Optional.of(owner));
        when(stsRunRepository.findFirstByOwner_KeycloakSubAndStatusOrderByUpdatedAtDesc(
                owner.getKeycloakSub(), StsRunStatus.Active)).thenReturn(Optional.empty());
        when(gameDataService.latestVersion()).thenReturn("2026-07-10.1");
        when(gameDataService.startingAct("2026-07-10.1")).thenReturn(2);
        when(gameDataService.character("2026-07-10.1", "MECA"))
                .thenReturn(objectMapper.readTree("{\"characterId\":\"MECA\",\"hp\":70}"));
        when(gameDataService.cards("2026-07-10.1"))
                .thenReturn(objectMapper.readTree("{\"cards\":[]}"));
        when(gameDataService.mapGeneration("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {"floors": 1, "nodesPerFloor": {"minInclusive": 1, "maxInclusive": 1}}
                """));
        when(stsRunRepository.save(any(StsRun.class))).thenAnswer(invocation -> {
            StsRun saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });

        StsRunStateDto state = stsRunService.startRun(owner, new StsStartRunRequest("MECA", "1.0.0"));

        assertThat(state.act()).isEqualTo(2);
    }

    /**
     * Prepare un catalogue de trois cartes, dont une secrete et la carte qui ouvre le pool, et une
     * table de recompense qui les propose toutes.
     */
    private void givenSecretCardCatalog(UUID runId, StsRun run) throws Exception {
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.rewardTables("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "gold": {"baseMinInclusive": 10, "baseMaxInclusive": 10},
                  "cardChoices": {"choicesPerReward": 5, "eliteCardEnchant": {"enabled": false}},
                  "tokens": {"grantRules": []}
                }
                """));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {"cards": [
                  {"id": "ordinaire", "favoredCharacter": "Aucun", "targetingMode": "Enemy", "tags": []},
                  {"id": "secrete", "favoredCharacter": "Aucun", "targetingMode": "Enemy", "tags": ["Secret"]},
                  {"id": "sesame", "favoredCharacter": "Aucun", "targetingMode": "Enemy", "tags": ["SecretUnlock"]}
                ]}
                """));
    }

    /**
     * La seule carte qu'un combat de cet acte peut proposer.
     *
     * <p>Les deux raretes se pesent 100 contre 0, donc le tirage n'a rien a trancher : ce qui sort
     * dit quelle table a ete lue.
     */
    private String rewardedCardIdForActIndex(int actIndex) throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = encounterRun(runId, "Combat", "encinst_combat", List.of("enemy_1"));
        run.setAct(actIndex);
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.rewardTables("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "gold": {"baseMinInclusive": 10, "baseMaxInclusive": 10},
                  "cardChoices": {
                    "choicesPerReward": 1,
                    "eliteCardEnchant": {"enabled": false},
                    "rarityWeights": {"normal": {"Common": 100, "Legendary": 0}},
                    "byAct": {
                      "2": {"rarityWeights": {"normal": {"Common": 0, "Legendary": 100}}}
                    }
                  },
                  "tokens": {"grantRules": []}
                }
                """));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {"cards": [
                  {"id": "commune", "rarity": "Common", "favoredCharacter": "Aucun", "targetingMode": "Enemy"},
                  {"id": "legendaire", "rarity": "Legendary", "favoredCharacter": "Aucun", "targetingMode": "Enemy"}
                ]}
                """));

        StsCompleteNodeResponse response = stsRunService.completeNode(
                owner, runId, 1,
                new StsCompleteNodeRequest("encinst_combat", "Victory", 3, 63, 7, List.of("enemy_1"), null));

        return cardChoiceIds(response).get(0);
    }

    private List<String> cardChoiceIds(StsCompleteNodeResponse response) throws Exception {
        List<String> cardIds = new ArrayList<>();
        for (JsonNode reward : json(response.pendingRewards())) {
            if (!"CardChoice".equals(reward.path("type").asText())) {
                continue;
            }
            for (JsonNode choice : reward.path("choices")) {
                cardIds.add(choice.path("cardId").asText());
            }
        }
        return cardIds;
    }

    /**
     * L'etat de run dit au client si le compte a ouvert le pool secret.
     *
     * <p>Sans lui, le client ne pouvait que deviner, et une carte secrete aperçue en recompense
     * ne se distinguait pas d'une fuite.
     */
    @Test
    void runStateTellsTheClientWhetherTheAccountUnlockedSecretCards() throws Exception {
        StsRun run = run(UUID.randomUUID());

        assertThat(stsRunService.toDto(run).secretCardsUnlocked()).isFalse();

        owner.setStsSecretCardsUnlockedAt(Instant.parse("2026-09-14T10:00:00Z"));

        assertThat(stsRunService.toDto(run).secretCardsUnlocked()).isTrue();
    }

    /**
     * Le menu principal lit l'état du pool secret sans qu'aucune run ne soit en cours.
     *
     * <p>C'est la réponse qu'il demande déjà pour savoir s'il y a une partie à reprendre : le
     * drapeau doit y figurer même quand la réponse est « pas de run ».
     */
    @Test
    void currentRunReportsTheSecretCardUnlockEvenWithoutARun() {
        when(stsRunRepository.findFirstByOwner_KeycloakSubAndStatusOrderByUpdatedAtDesc(
                owner.getKeycloakSub(), StsRunStatus.Active)).thenReturn(Optional.empty());

        assertThat(stsRunService.currentRun(owner).secretCardsUnlocked()).isFalse();

        owner.setStsSecretCardsUnlockedAt(Instant.parse("2026-09-14T10:00:00Z"));

        assertThat(stsRunService.currentRun(owner))
                .satisfies(response -> {
                    assertThat(response.hasRun()).isFalse();
                    assertThat(response.secretCardsUnlocked()).isTrue();
                });
    }

    /**
     * Une carte secrete deja inscrite dans une recompense non prise reste cachee a un compte verrouille.
     *
     * <p>Une recompense est tiree une fois puis conservee avec la run : un Booster tire avant que
     * la generation n'ecarte les cartes secretes les garde, et les montrait encore. Le compte n'a
     * pourtant jamais ouvert le pool.
     */
    @Test
    void aSecretCardAlreadyInAnUnclaimedRewardIsHiddenUntilTheAccountUnlocksIt() throws Exception {
        StsRun run = run(UUID.randomUUID());
        run.setPendingRewards(objectMapper.readTree(STALE_SECRET_REWARD));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree(SECRET_CATALOG));

        assertThat(cardChoiceIdsOf(stsRunService.toDto(run).pendingRewards()))
                .containsExactly("ordinaire");
        assertThat(run.getPendingRewards().path(0).path("choices"))
                .as("the stored reward is not rewritten, only what is sent")
                .hasSize(2);

        owner.setStsSecretCardsUnlockedAt(Instant.parse("2026-09-14T10:00:00Z"));

        assertThat(cardChoiceIdsOf(stsRunService.toDto(run).pendingRewards()))
                .containsExactly("secrete", "ordinaire");
    }

    /** Cacher ne suffit pas : la prise d'une carte secrete par un compte verrouille est refusee. */
    @Test
    void aLockedAccountCannotClaimASecretCardFromAnOldReward() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = run(runId);
        run.setPendingRewards(objectMapper.readTree(STALE_SECRET_REWARD));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree(SECRET_CATALOG));

        assertThatThrownBy(() -> stsRunService.claimReward(
                owner, runId, "reward_booster", new StsClaimRewardRequest("secrete")))
                .isInstanceOf(fr.insastronaute.webapi.common.web.BadRequestException.class);
        assertThat(run.getRunInventory().path("deck")).isEmpty();
    }

    private static final String STALE_SECRET_REWARD = """
            [{"rewardId":"reward_booster","type":"CardChoice","claimed":false,
              "choices":[{"cardId":"secrete","enchantments":[]},{"cardId":"ordinaire","enchantments":[]}]}]
            """;

    private static final String SECRET_CATALOG = """
            {"cards": [
              {"id": "ordinaire", "favoredCharacter": "Aucun", "targetingMode": "Enemy", "tags": []},
              {"id": "secrete", "favoredCharacter": "Aucun", "targetingMode": "Enemy", "tags": ["Secret"]}
            ]}
            """;

    private List<String> cardChoiceIdsOf(Object pendingRewards) throws Exception {
        List<String> cardIds = new ArrayList<>();
        for (JsonNode reward : json(pendingRewards)) {
            for (JsonNode choice : reward.path("choices")) {
                cardIds.add(choice.path("cardId").asText());
            }
        }
        return cardIds;
    }

    /**
     * Sans reglage, une defaite ne rapporte toujours rien -- et ne va meme pas chercher le compte.
     */
    @Test
    void aGameOverGrantsNoTokensWithoutAMultiplier() throws Exception {
        assertThat(tokensGrantedOnDefeat(null)).isZero();
        assertThat(owner.getTokens()).isZero();
        verifyNoInteractions(userRepository);
    }

    /** Avec un multiplicateur, une defaite rapporte, et le compte est credite de ce qu'elle annonce. */
    @Test
    void aGameOverGrantsTokensWhenAMultiplierIsConfigured() throws Exception {
        long granted = tokensGrantedOnDefeat(0.25);

        assertThat(granted).isPositive();
        assertThat(owner.getTokens()).isEqualTo(granted);
    }

    /**
     * Le multiplicateur est bien la part de ce qu'un retrait au meme point aurait rendu.
     *
     * <p>Compare deux defaites identiques, l'une payee en entier, l'autre au quart : la relation
     * se verifie sans avoir a refaire ici le calcul du score.
     */
    @Test
    void theGameOverMultiplierIsAFractionOfTheFullReward() throws Exception {
        long full = tokensGrantedOnDefeat(1.0);
        long quarter = tokensGrantedOnDefeat(0.25);

        assertThat(full).isPositive();
        assertThat(quarter).isEqualTo((long) Math.floor(full * 0.25));
    }

    /** Les jetons qu'une defaite rapporte avec ce multiplicateur, ou sans reglage quand il est null. */
    private long tokensGrantedOnDefeat(Double gameOverMultiplier) throws Exception {
        owner.setTokens(0L);
        UUID runId = UUID.randomUUID();
        StsRun run = encounterRun(runId, "Combat", "encinst_combat", List.of("enemy_1"));
        when(stsRunRepository.findOwnedWithLock(runId, owner.getKeycloakSub())).thenReturn(Optional.of(run));
        when(gameDataService.runScoreRewards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "score": {
                    "visitedNode": 25, "combatVictory": 10, "eliteVictory": 20, "eventVisited": 5,
                    "actReached": 50, "relicOwned": 10, "deckCard": 5, "goldOwned": 1,
                    "remainingHpPercent": 2
                  },
                  "tokens": {"scorePerToken": 20, "minimumReward": 10, "rounding": "NEAREST"%s}
                }
                """.formatted(gameOverMultiplier == null ? "" : ", \"gameOverMultiplier\": " + gameOverMultiplier)));
        if (gameOverMultiplier != null && gameOverMultiplier > 0) {
            when(userRepository.findWithLockByKeycloakSub(owner.getKeycloakSub())).thenReturn(Optional.of(owner));
        }

        StsCompleteNodeResponse response = stsRunService.completeNode(
                owner, runId, 1,
                new StsCompleteNodeRequest("encinst_combat", "Defeat", 3, 0, 7, List.of(), null));

        assertThat(run.getStatus()).isEqualTo(StsRunStatus.Failed);
        return json(response.accountInventoryPatch()).path("tokenDelta").asLong();
    }

    private StsRun encounterRun(UUID runId, String nodeType, String encounterInstanceId, List<String> enemyIds) throws Exception {
        StsRun run = run(runId);
        run.setEnteredNodeId(1);
        run.setMapState(objectMapper.readTree("""
                {
                  "nodes": [
                    {
                      "id": 0,
                      "floor": 0,
                      "type": "Start",
                      "visited": true,
                      "completed": true,
                      "nextIds": [1]
                    },
                    {
                      "id": 1,
                      "floor": 1,
                      "type": "%s",
                      "visited": true,
                      "completed": false,
                      "nextIds": []
                    }
                  ]
                }
                """.formatted(nodeType)));

        StringBuilder encounterJson = new StringBuilder();
        encounterJson.append("{\"encounterInstanceId\":\"").append(encounterInstanceId).append("\",");
        encounterJson.append("\"encounterId\":\"").append(nodeType.toLowerCase()).append("_encounter\",");
        encounterJson.append("\"enemyIds\":[");
        for (int i = 0; i < enemyIds.size(); i++) {
            if (i > 0) {
                encounterJson.append(",");
            }
            encounterJson.append("\"").append(enemyIds.get(i)).append("\"");
        }
        encounterJson.append("]}");
        run.setActiveEncounter(objectMapper.readTree(encounterJson.toString()));
        run.setActiveEvent(null);
        return run;
    }

    private JsonNode retirementRules() throws Exception {
        return objectMapper.readTree("""
                {
                  "score": {},
                  "tokens": {"scorePerToken":20,"minimumReward":10,"rounding":"NEAREST"}
                }
                """);
    }

    private StsRun run(UUID runId) throws Exception {
        StsRun run = new StsRun();
        ReflectionTestUtils.setField(run, "id", runId);
        run.setOwner(owner);
        run.setStatus(StsRunStatus.Active);
        run.setDataVersion("2026-07-10.1");
        run.setSelectedCharacter("MECA");
        run.setAct(0);
        run.setCurrentFloor(0);
        run.setCurrentNodeId(0);
        run.setPlayerState(objectMapper.readTree("{\"maxHp\":70,\"currentHp\":70}"));
        run.setRunInventory(objectMapper.readTree("{\"gold\":0,\"deck\":[],\"relics\":[]}"));
        run.setPendingRewards(objectMapper.createArrayNode());
        run.setMapState(objectMapper.readTree("""
                {
                  "nodes": [
                    {
                      "id": 0,
                      "floor": 0,
                      "type": "Start",
                      "visited": true,
                      "completed": true,
                      "nextIds": [1]
                    },
                    {
                      "id": 1,
                      "floor": 1,
                      "type": "Event",
                      "visited": false,
                      "completed": false,
                      "nextIds": []
                    }
                  ]
                }
                """));
        return run;
    }

    private JsonNode minimalMapConfig() throws Exception {
        return objectMapper.readTree("""
                {
                  "height": 1,
                  "startNode": {
                    "floor": 0,
                    "type": "Start",
                    "posX": 0.5,
                    "visited": true,
                    "completed": true
                  }
                }
                """);
    }

    private JsonNode json(Object value) {
        return objectMapper.valueToTree(value);
    }

    // --- Feu de camp autoritatif ---------------------------------------------

    @Test
    void enteringARestSiteFillsTheChargesAndPlaysTheRestRelics() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = restRun(runId, false);
        run.setPlayerState(objectMapper.readTree("{\"maxHp\":80,\"currentHp\":30}"));
        run.setRunInventory(objectMapper.readTree(
                "{\"gold\":0,\"deck\":[],\"relics\":[{\"relicId\":\"RestHealRelic\"},{\"relicId\":\"RestChargesRelic\"}]}"));
        givenRelicEngine();
        when(stsRunRepository.findOwnedWithLock(runId, "sub-1")).thenReturn(Optional.of(run));

        stsRunService.enterNode(owner, runId, 1, null);

        assertThat(run.getPlayerState().path("restCharges").asInt()).isEqualTo(4);
        assertThat(run.getPlayerState().path("maxRestCharges").asInt()).isEqualTo(3);
        assertThat(run.getPlayerState().path("currentHp").asInt()).isEqualTo(35);
    }

    @Test
    void restingHealsATwelfthOfMaxHpPerChargeAndStopsWhenFull() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = restRun(runId, true);
        run.setPlayerState(objectMapper.readTree(
                "{\"maxHp\":72,\"currentHp\":60,\"restCharges\":5,\"maxRestCharges\":15}"));
        when(stsRunRepository.findOwnedWithLock(runId, "sub-1")).thenReturn(Optional.of(run));

        var response = stsRunService.rest(owner, runId, 1, new StsRestActionRequest("HEAL", null, null));

        // 72/12 = 6 par charge : deux charges suffisent à combler les 12 points
        // manquants, les trois autres restent pour enchanter.
        assertThat(run.getPlayerState().path("currentHp").asInt()).isEqualTo(72);
        assertThat(response.restCharges()).isEqualTo(3);
    }

    @Test
    void enchantingSpendsItsChargesAndWritesTheEnchantmentIntoTheDeck() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = restRun(runId, true);
        run.setPlayerState(objectMapper.readTree(
                "{\"maxHp\":80,\"currentHp\":80,\"restCharges\":5,\"maxRestCharges\":15}"));
        run.setRunInventory(objectMapper.readTree(
                "{\"gold\":0,\"relics\":[],\"deck\":[{\"instanceId\":\"cardinst_1\",\"cardId\":\"katana\",\"enchantments\":[]}]}"));
        when(stsRunRepository.findOwnedWithLock(runId, "sub-1")).thenReturn(Optional.of(run));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {
                  "cards": [
                    { "id": "katana", "targetingMode": "Enemy", "favoredCharacter": "Starting", "cost": 1 }
                  ]
                }
                """));

        var response = stsRunService.rest(
                owner, runId, 1, new StsRestActionRequest("ENCHANT", "cardinst_1", 2));

        assertThat(response.restCharges()).isEqualTo(3);
        assertThat(run.getRunInventory().path("deck").get(0).path("enchantments")).isNotEmpty();
    }

    @Test
    void refusesToEnchantWithMoreChargesThanTheRunHas() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = restRun(runId, true);
        run.setPlayerState(objectMapper.readTree(
                "{\"maxHp\":80,\"currentHp\":80,\"restCharges\":1,\"maxRestCharges\":15}"));
        when(stsRunRepository.findOwnedWithLock(runId, "sub-1")).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> stsRunService.rest(
                owner, runId, 1, new StsRestActionRequest("ENCHANT", "cardinst_1", 3)))
                .hasMessage("Charges de feu de camp STS insuffisantes.");
    }

    @Test
    void refusesToEnchantACardThatIsNotInTheDeck() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = restRun(runId, true);
        run.setPlayerState(objectMapper.readTree(
                "{\"maxHp\":80,\"currentHp\":80,\"restCharges\":5,\"maxRestCharges\":15}"));
        when(stsRunRepository.findOwnedWithLock(runId, "sub-1")).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> stsRunService.rest(
                owner, runId, 1, new StsRestActionRequest("ENCHANT", "cardinst_absente", 1)))
                .hasMessage("Une carte STS sélectionnée est introuvable dans le deck.");
    }

    @Test
    void refusesARestActionOnANodeThatIsNotARestSite() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = run(runId); // le node 1 y est un Event
        run.setEnteredNodeId(1);
        when(stsRunRepository.findOwnedWithLock(runId, "sub-1")).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> stsRunService.rest(
                owner, runId, 1, new StsRestActionRequest("HEAL", null, null)))
                .hasMessage("Le node STS courant n'est pas un feu de camp.");
    }

    /**
     * Une run déjà à l'intérieur d'un feu de camp au moment du déploiement n'a pas
     * de charges en base. Sans rattrapage, l'endpoint les verrait à zéro et
     * refuserait tout, laissant le joueur devant un feu de camp inerte.
     */
    @Test
    void aRunAlreadyInsideARestSiteGetsItsChargesOnFirstUse() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = restRun(runId, true);
        run.setPlayerState(objectMapper.readTree("{\"maxHp\":72,\"currentHp\":30}"));
        givenRelicEngine();
        when(stsRunRepository.findOwnedWithLock(runId, "sub-1")).thenReturn(Optional.of(run));

        var response = stsRunService.rest(owner, runId, 1, new StsRestActionRequest("HEAL", null, null));

        assertThat(response.restCharges()).isZero();
        assertThat(run.getPlayerState().path("currentHp").asInt()).isEqualTo(48);
    }

    /**
     * C'est la faille d'origine : le client annonçait ses points de vie et le
     * serveur les écrivait. Rien d'autre ne signalerait sa réapparition.
     */
    @Test
    void completingARestSiteIgnoresThePlayerHpTheClientClaims() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = restRun(runId, true);
        run.setPlayerState(objectMapper.readTree(
                "{\"maxHp\":80,\"currentHp\":30,\"restCharges\":0,\"maxRestCharges\":15}"));
        when(stsRunRepository.findOwnedWithLock(runId, "sub-1")).thenReturn(Optional.of(run));

        stsRunService.completeNode(owner, runId, 1, new StsCompleteNodeRequest(
                null, "rest", 0, 80, 0, List.of(), null));

        assertThat(run.getPlayerState().path("currentHp").asInt())
                .as("le serveur garde les PV qu'il tient")
                .isEqualTo(30);
    }

    private void givenRelicEngine() {
        when(stsAuthoritativeCombatService.relicEngine()).thenReturn(
                new fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine(List.of(
                        new fr.insastronaute.webapi.sts.combat.engine.relic.handlers.RestChargesRelicHandler(),
                        new fr.insastronaute.webapi.sts.combat.engine.relic.handlers.RestHealRelicHandler(),
                        new fr.insastronaute.webapi.sts.combat.engine.relic.handlers.FieldRationsRelicHandler())));
    }

    private StsRun restRun(UUID runId, boolean entered) throws Exception {
        StsRun run = run(runId);
        run.setMapState(objectMapper.readTree("""
                {
                  "nodes": [
                    { "id": 0, "floor": 0, "type": "Start", "visited": true, "completed": true, "nextIds": [1] },
                    { "id": 1, "floor": 1, "type": "Rest", "visited": false, "completed": false, "nextIds": [] }
                  ]
                }
                """));
        if (entered) {
            run.setEnteredNodeId(1);
        }
        return run;
    }

    // --- Ce que le serveur écrit quand on gagne une carte --------------------

    /**
     * Une carte gagnée entrait au deck en visant systématiquement un ennemi.
     *
     * <p>Le deck de départ, lui, lit la visée du catalogue. Un soin ou une armure obtenus après
     * un combat se retrouvaient donc à réclamer une cible ennemie pour se lancer sur soi.
     */
    @Test
    void aWonCardEntersTheDeckAimedWhereItsAuthorAimedIt() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = run(runId);
        run.setPendingRewards(objectMapper.readTree("""
                [{"rewardId":"reward_card","type":"CardChoice","claimed":false,
                  "choices":[{"cardId":"panseur","enchantments":[]}]}]
                """));
        when(stsRunRepository.findOwnedWithLock(runId, "sub-1")).thenReturn(Optional.of(run));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {"cards":[{"id":"panseur","targetingMode":"Player","favoredCharacter":"MECA","cost":1}]}
                """));

        stsRunService.claimReward(owner, runId, "reward_card", new StsClaimRewardRequest("panseur"));

        assertThat(run.getRunInventory().path("deck").get(0).path("targetingMode").asText())
                .isEqualTo("Player");
    }

    /**
     * Le feu de camp doit reconnaître une carte gagnée au combat précédent.
     *
     * <p>C'est le serveur qui crée l'instance et lui donne son identifiant ; le client, lui,
     * s'en inventait un et inscrivait sa propre copie. Les deux decks cessaient alors de
     * désigner la même carte : le feu de camp la proposait à l'enchantement, le serveur ne la
     * trouvait nulle part, et le panneau se refermait sans rien enchanter ni consommer de
     * charge. Le tour complet est donc ce qu'il faut vérifier — l'identifiant que la
     * réclamation rend est celui que l'enchantement accepte.
     */
    @Test
    void theCampfireEnchantsACardWonInTheFightBefore() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = restRun(runId, true);
        run.setPlayerState(objectMapper.readTree(
                "{\"maxHp\":80,\"currentHp\":80,\"restCharges\":3,\"maxRestCharges\":3}"));
        run.setPendingRewards(objectMapper.readTree("""
                [{"rewardId":"reward_card","type":"CardChoice","claimed":false,
                  "choices":[{"cardId":"katana","enchantments":[]}]}]
                """));
        when(stsRunRepository.findOwnedWithLock(runId, "sub-1")).thenReturn(Optional.of(run));
        when(gameDataService.cards("2026-07-10.1")).thenReturn(objectMapper.readTree("""
                {"cards":[{"id":"katana","targetingMode":"Enemy","favoredCharacter":"Starting","cost":1}]}
                """));

        var claim = stsRunService.claimReward(
                owner, runId, "reward_card", new StsClaimRewardRequest("katana"));
        String instanceId = json(claim.runInventory()).path("deck").get(0).path("instanceId").asText();

        assertThat(instanceId).isNotBlank();

        var enchanted = stsRunService.rest(
                owner, runId, 1, new StsRestActionRequest("ENCHANT", instanceId, 2));

        assertThat(enchanted.restCharges()).isEqualTo(1);
        assertThat(run.getRunInventory().path("deck").get(0).path("enchantments")).isNotEmpty();
    }
}
