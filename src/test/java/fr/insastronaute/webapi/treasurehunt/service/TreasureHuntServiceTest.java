package fr.insastronaute.webapi.treasurehunt.service;

import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntInputTool;
import fr.insastronaute.webapi.treasurehunt.dto.PlayerPathProgressResponse;
import fr.insastronaute.webapi.treasurehunt.domain.PlayerVaultCode;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntCheckpoint;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntContent;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntContentType;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntPath;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntSettings;
import fr.insastronaute.webapi.treasurehunt.repository.PlayerCheckpointProgressRepository;
import fr.insastronaute.webapi.treasurehunt.repository.PlayerVaultCodeRepository;
import fr.insastronaute.webapi.treasurehunt.repository.PlayerVaultUnlockRepository;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntCheckpointRepository;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntSettingsRepository;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntPathRepository;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntVaultRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TreasureHuntServiceTest {
    @Test
    void returnsPersistedProgressForEveryConfiguredPath() {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        TreasureHuntPath gmPath = path(1, "GM");
        TreasureHuntPath epPath = path(2, "EP");
        PlayerVaultCode epVaultCode = new PlayerVaultCode();
        epVaultCode.setPath(epPath);
        epVaultCode.setCode("EP-FINAL");
        TreasureHuntSettings settings = new TreasureHuntSettings();
        settings.setActive(true);

        UserRepository users = mock(UserRepository.class);
        TreasureHuntCheckpointRepository checkpoints = mock(TreasureHuntCheckpointRepository.class);
        PlayerCheckpointProgressRepository progress = mock(PlayerCheckpointProgressRepository.class);
        PlayerVaultCodeRepository vaultCodes = mock(PlayerVaultCodeRepository.class);
        PlayerVaultUnlockRepository unlocks = mock(PlayerVaultUnlockRepository.class);
        TreasureHuntVaultRepository vault = mock(TreasureHuntVaultRepository.class);
        TreasureHuntStorageService storage = mock(TreasureHuntStorageService.class);
        TreasureHuntSettingsRepository settingsRepository = mock(TreasureHuntSettingsRepository.class);
        TreasureHuntPathRepository paths = mock(TreasureHuntPathRepository.class);
        when(settingsRepository.findById(true)).thenReturn(Optional.of(settings));
        when(users.findByKeycloakSub("sub")).thenReturn(Optional.of(user));
        when(paths.findAllByOrderByDepartmentShortNameAsc()).thenReturn(List.of(epPath, gmPath));
        when(progress.countByUserIdAndCheckpointPathId(userId, gmPath.getId())).thenReturn(2L);
        when(progress.countByUserIdAndCheckpointPathId(userId, epPath.getId())).thenReturn(3L);
        when(vaultCodes.findByUserIdOrderByPathDepartmentShortNameAsc(userId)).thenReturn(List.of(epVaultCode));

        TreasureHuntService service = new TreasureHuntService(
                users, checkpoints, progress, vaultCodes, unlocks, vault, storage, settingsRepository, paths,
                mock(VaultCodeGenerator.class), mock(AutoChallengeTriggerPublisher.class));

        // Par departement, et sans position : l'ordre des voies est la combinaison du
        // coffre, et il ne doit se lire ni dans un champ ni dans l'ordre du tableau.
        assertThat(service.getProgress("sub").paths())
                .extracting("department", "validatedStepCount", "vaultCode")
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("EP", 3L, "EP-FINAL"),
                        org.assertj.core.api.Assertions.tuple("GM", 2L, null)
                );
        assertThat(PlayerPathProgressResponse.class.getRecordComponents())
                .extracting("name").doesNotContain("pathPosition");
    }

    @Test
    void aHiddenHuntIsClosedEvenWhenItIsActive() {
        TreasureHuntSettings settings = new TreasureHuntSettings();
        settings.setActive(true);
        settings.setHidden(true);

        TreasureHuntSettingsRepository settingsRepository = mock(TreasureHuntSettingsRepository.class);
        when(settingsRepository.findById(true)).thenReturn(Optional.of(settings));

        TreasureHuntService service = new TreasureHuntService(
                mock(UserRepository.class), mock(TreasureHuntCheckpointRepository.class),
                mock(PlayerCheckpointProgressRepository.class), mock(PlayerVaultCodeRepository.class),
                mock(PlayerVaultUnlockRepository.class), mock(TreasureHuntVaultRepository.class),
                mock(TreasureHuntStorageService.class), settingsRepository, mock(TreasureHuntPathRepository.class),
                mock(VaultCodeGenerator.class), mock(AutoChallengeTriggerPublisher.class));

        assertThat(service.isVisible()).isFalse();
        // Un lien gardé en favori ne doit pas rouvrir ce qu'on a retiré du front.
        assertThatThrownBy(() -> service.getProgress("sub"))
                .isInstanceOf(fr.insastronaute.webapi.common.web.ForbiddenException.class);
    }

    @Test
    void aHuntIsVisibleUntilSomeoneHidesIt() {
        TreasureHuntSettingsRepository settingsRepository = mock(TreasureHuntSettingsRepository.class);
        when(settingsRepository.findById(true)).thenReturn(Optional.empty());

        TreasureHuntService service = new TreasureHuntService(
                mock(UserRepository.class), mock(TreasureHuntCheckpointRepository.class),
                mock(PlayerCheckpointProgressRepository.class), mock(PlayerVaultCodeRepository.class),
                mock(PlayerVaultUnlockRepository.class), mock(TreasureHuntVaultRepository.class),
                mock(TreasureHuntStorageService.class), settingsRepository, mock(TreasureHuntPathRepository.class),
                mock(VaultCodeGenerator.class), mock(AutoChallengeTriggerPublisher.class));

        assertThat(service.isVisible()).isTrue();
    }

    // Le code d'une etape reste sur sa balise : sans cette relecture, le contenu gagne
    // n'etait visible qu'une fois, a la seconde ou le code partait.
    @Test
    void listsTheContentOfTheCheckpointsThePlayerAlreadyValidated() {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        TreasureHuntPath gmPath = path(1, "GM");
        TreasureHuntCheckpoint firstStep = new TreasureHuntCheckpoint();
        firstStep.setId(UUID.randomUUID());
        firstStep.setPath(gmPath);
        firstStep.setStepNumber(1);
        firstStep.setCode("GM-1");
        TreasureHuntContent indice = new TreasureHuntContent();
        indice.setPosition(1);
        indice.setType(TreasureHuntContentType.RICH_TEXT);
        indice.setText("Premiere ligne\nSeconde ligne");
        firstStep.addContent(indice);
        TreasureHuntSettings settings = new TreasureHuntSettings();
        settings.setActive(true);

        UserRepository users = mock(UserRepository.class);
        PlayerCheckpointProgressRepository progress = mock(PlayerCheckpointProgressRepository.class);
        TreasureHuntSettingsRepository settingsRepository = mock(TreasureHuntSettingsRepository.class);
        when(settingsRepository.findById(true)).thenReturn(Optional.of(settings));
        when(users.findByKeycloakSub("sub")).thenReturn(Optional.of(user));
        when(progress.findValidatedCheckpoints(userId)).thenReturn(List.of(firstStep));

        TreasureHuntService service = new TreasureHuntService(
                users, mock(TreasureHuntCheckpointRepository.class), progress, mock(PlayerVaultCodeRepository.class),
                mock(PlayerVaultUnlockRepository.class), mock(TreasureHuntVaultRepository.class),
                mock(TreasureHuntStorageService.class), settingsRepository, mock(TreasureHuntPathRepository.class),
                mock(VaultCodeGenerator.class), mock(AutoChallengeTriggerPublisher.class));

        assertThat(service.getValidatedCheckpoints("sub").checkpoints())
                .extracting("pathId", "stepNumber")
                .containsExactly(org.assertj.core.api.Assertions.tuple(gmPath.getId(), 1));
        assertThat(service.getValidatedCheckpoints("sub").checkpoints().get(0).contents())
                .extracting("type", "text")
                .containsExactly(org.assertj.core.api.Assertions.tuple(
                        TreasureHuntContentType.RICH_TEXT, "Premiere ligne\nSeconde ligne"));
    }

    // Une etape jamais franchie ne se relit pas : la liste ne sort que du journal du joueur.
    @Test
    void neverGivesAwayACheckpointThePlayerHasNotValidated() {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        TreasureHuntSettings settings = new TreasureHuntSettings();
        settings.setActive(true);

        UserRepository users = mock(UserRepository.class);
        PlayerCheckpointProgressRepository progress = mock(PlayerCheckpointProgressRepository.class);
        TreasureHuntCheckpointRepository checkpoints = mock(TreasureHuntCheckpointRepository.class);
        TreasureHuntSettingsRepository settingsRepository = mock(TreasureHuntSettingsRepository.class);
        when(settingsRepository.findById(true)).thenReturn(Optional.of(settings));
        when(users.findByKeycloakSub("sub")).thenReturn(Optional.of(user));
        when(progress.findValidatedCheckpoints(userId)).thenReturn(List.of());

        TreasureHuntService service = new TreasureHuntService(
                users, checkpoints, progress, mock(PlayerVaultCodeRepository.class),
                mock(PlayerVaultUnlockRepository.class), mock(TreasureHuntVaultRepository.class),
                mock(TreasureHuntStorageService.class), settingsRepository, mock(TreasureHuntPathRepository.class),
                mock(VaultCodeGenerator.class), mock(AutoChallengeTriggerPublisher.class));

        assertThat(service.getValidatedCheckpoints("sub").checkpoints()).isEmpty();
        verifyNoInteractions(checkpoints);
    }

    private static TreasureHuntPath path(int position, String departmentShortName) {
        Department department = new Department();
        department.setShortName(departmentShortName);
        TreasureHuntPath path = new TreasureHuntPath();
        path.setId(UUID.randomUUID());
        path.setPosition(position);
        path.setDepartment(department);
        return path;
    }

    @Test
    void rejectsAValidCodeSubmittedForAnotherPath() {
        UUID checkpointPathId = UUID.randomUUID();
        UUID selectedPathId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        TreasureHuntPath checkpointPath = new TreasureHuntPath();
        checkpointPath.setId(checkpointPathId);
        TreasureHuntCheckpoint checkpoint = new TreasureHuntCheckpoint();
        checkpoint.setId(checkpointId);
        checkpoint.setPath(checkpointPath);
        checkpoint.setStepNumber(1);
        TreasureHuntSettings settings = new TreasureHuntSettings();
        settings.setActive(true);
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());

        UserRepository users = mock(UserRepository.class);
        TreasureHuntCheckpointRepository checkpoints = mock(TreasureHuntCheckpointRepository.class);
        PlayerCheckpointProgressRepository progress = mock(PlayerCheckpointProgressRepository.class);
        PlayerVaultCodeRepository vaultCodes = mock(PlayerVaultCodeRepository.class);
        PlayerVaultUnlockRepository unlocks = mock(PlayerVaultUnlockRepository.class);
        TreasureHuntVaultRepository vault = mock(TreasureHuntVaultRepository.class);
        TreasureHuntStorageService storage = mock(TreasureHuntStorageService.class);
        TreasureHuntSettingsRepository settingsRepository = mock(TreasureHuntSettingsRepository.class);
        TreasureHuntPathRepository paths = mock(TreasureHuntPathRepository.class);
        when(settingsRepository.findById(true)).thenReturn(Optional.of(settings));
        when(users.findWithLockByKeycloakSub("sub")).thenReturn(Optional.of(user));
        when(checkpoints.findByCodeHash(any())).thenReturn(Optional.of(checkpoint));

        TreasureHuntService service = new TreasureHuntService(
                users, checkpoints, progress, vaultCodes, unlocks, vault, storage, settingsRepository, paths,
                mock(VaultCodeGenerator.class), mock(AutoChallengeTriggerPublisher.class));

        // Le secret est reconnu (meme outil, ici) : le pathId errone se decouvre donc
        // apres la meme requete "deja validee" qu'un outil errone aurait declenchee.
        assertThatThrownBy(() -> service.validateCheckpoint("sub", "KEYPAD", "valid-code", selectedPathId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(TreasureHuntService.REFUSAL);
        verify(progress).existsByUserIdAndCheckpointId(user.getId(), checkpointId);
        verifyNoInteractions(vaultCodes);
    }

    // Un secret juste soumis par le mauvais outil est refuse comme un secret faux :
    // c'est toute la raison d'etre des leurres.
    @Test
    void refusesARightSecretSubmittedThroughTheWrongTool() {
        ToolFixture fixture = toolFixture(TreasureHuntInputTool.PIANO, "DO-RE-MI", 1, true);

        assertThatThrownBy(() -> fixture.service().validateCheckpoint("sub", "KEYPAD", "DO-RE-MI", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(TreasureHuntService.REFUSAL);
    }

    // Secret inconnu, outil faux, etape prematuree, melodie malformee : un seul message.
    // Toute distinction designerait l'outil attendu.
    @Test
    void opposesOneAndTheSameRefusalToEveryFailure() {
        ToolFixture tooEarly = toolFixture(TreasureHuntInputTool.PIANO, "DO-RE-MI", 2, false);
        ToolFixture reachable = toolFixture(TreasureHuntInputTool.PIANO, "DO-RE-MI", 1, true);

        assertThatThrownBy(() -> tooEarly.service().validateCheckpoint("sub", "PIANO", "DO-RE-MI", null))
                .isInstanceOf(BadRequestException.class).hasMessage(TreasureHuntService.REFUSAL);
        assertThatThrownBy(() -> reachable.service().validateCheckpoint("sub", "PIANO", "DO-H-MI", null))
                .isInstanceOf(BadRequestException.class).hasMessage(TreasureHuntService.REFUSAL);
        assertThatThrownBy(() -> reachable.service().validateCheckpoint("sub", "DIAL", "4-7-2", null))
                .isInstanceOf(BadRequestException.class).hasMessage(TreasureHuntService.REFUSAL);
    }

    // Le cout d'un refus ne doit pas dependre de l'outil. Les deux requetes de progression
    // portaient sur la seule branche retenue, si bien qu'une etape piano "DO RE MI" soumise
    // au piano coutait deux requetes de plus que le meme texte soumis au clavier ou par un
    // leurre -- la branche retenue etait vide et le refus tombait avant elles. Chronometrer
    // les trois suffisait a isoler le piano.
    @Test
    void spendsTheSameProgressQueriesWhicheverToolARefusedSecretCameThrough() {
        ToolFixture piano = toolFixture(TreasureHuntInputTool.PIANO, "DO-RE-MI", 2, false);
        ToolFixture keypad = toolFixture(TreasureHuntInputTool.PIANO, "DO-RE-MI", 2, false);
        ToolFixture decoy = toolFixture(TreasureHuntInputTool.PIANO, "DO-RE-MI", 2, false);

        // Etape prematuree au piano, mauvais outil au clavier, mauvais outil au telegraphe :
        // un seul et meme refus.
        assertThatThrownBy(() -> piano.service().validateCheckpoint("sub", "PIANO", "DO RE MI", null))
                .isInstanceOf(BadRequestException.class).hasMessage(TreasureHuntService.REFUSAL);
        assertThatThrownBy(() -> keypad.service().validateCheckpoint("sub", "KEYPAD", "DO RE MI", null))
                .isInstanceOf(BadRequestException.class).hasMessage(TreasureHuntService.REFUSAL);
        assertThatThrownBy(() -> decoy.service().validateCheckpoint("sub", "TELEGRAPH", "DO RE MI", null))
                .isInstanceOf(BadRequestException.class).hasMessage(TreasureHuntService.REFUSAL);

        // Et au meme prix : deux recherches de secret et deux requetes de progression chacun.
        for (ToolFixture fixture : List.of(piano, keypad, decoy)) {
            verify(fixture.checkpoints(), times(2)).findByCodeHash(any());
            verify(fixture.progress()).existsByUserIdAndCheckpointId(fixture.userId(), fixture.checkpointId());
            verify(fixture.progress()).existsByUserIdAndCheckpointPathIdAndCheckpointStepNumber(
                    fixture.userId(), fixture.pathId(), 1);
            verifyNoMoreInteractions(fixture.progress());
        }
    }

    // toolOf() rattrape un nom d'outil qui ne correspond a aucune valeur de l'enum : ce
    // cas doit rendre le refus generique, pas une exception non geree (un 500).
    @Test
    void refusesAnUnknownToolNameInsteadOfLettingItThrow() {
        ToolFixture fixture = toolFixture(TreasureHuntInputTool.KEYPAD, "GM-1", 1, true);

        assertThatThrownBy(() -> fixture.service().validateCheckpoint("sub", "DIAPASON", "GM-1", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(TreasureHuntService.REFUSAL);
    }

    // Un ancien client peut ne pas envoyer d'outil du tout : ce cas se rabat sur KEYPAD,
    // et non sur une exception, car c'est le seul outil que la chasse connaissait avant.
    @Test
    void treatsAMissingToolAsKeypadForBackwardCompatibility() {
        ToolFixture fixture = toolFixture(TreasureHuntInputTool.KEYPAD, "GM-1", 1, true);

        assertThat(fixture.service().validateCheckpoint("sub", null, "GM-1", null).stepNumber())
                .isEqualTo(1);
    }

    // La melodie arrive telle que le joueur l'a ecrite ; c'est le serveur qui la replie.
    @Test
    void acceptsAMelodyWhateverTheFrontsWritingOfIt() {
        ToolFixture fixture = toolFixture(TreasureHuntInputTool.PIANO, "DO-RE-MI", 1, true);

        assertThat(fixture.service().validateCheckpoint("sub", "piano", "do ré mi", null).stepNumber())
                .isEqualTo(1);
    }

    // pathId n'est plus envoye par le front, mais un client anterieur continue de marcher.
    @Test
    void stillHonoursAPathIdWhenAnOlderClientSendsOne() {
        ToolFixture fixture = toolFixture(TreasureHuntInputTool.KEYPAD, "GM-1", 1, true);

        assertThat(fixture.service().validateCheckpoint("sub", "KEYPAD", "GM-1", fixture.pathId()).stepNumber())
                .isEqualTo(1);
        assertThatThrownBy(() -> fixture.service()
                .validateCheckpoint("sub", "KEYPAD", "GM-1", UUID.randomUUID()))
                .isInstanceOf(BadRequestException.class).hasMessage(TreasureHuntService.REFUSAL);
    }

    @Test
    void aCheckpointCarriesItsCompositeContentsInOrder() {
        TreasureHuntCheckpoint checkpoint = new TreasureHuntCheckpoint();
        TreasureHuntContent text = new TreasureHuntContent(); text.setPosition(1); text.setType(TreasureHuntContentType.RICH_TEXT);
        TreasureHuntContent finalCode = new TreasureHuntContent(); finalCode.setPosition(2); finalCode.setType(TreasureHuntContentType.FINAL_CODE);
        checkpoint.addContent(text); checkpoint.addContent(finalCode);

        assertThat(checkpoint.getContents()).extracting(TreasureHuntContent::getType)
                .containsExactly(TreasureHuntContentType.RICH_TEXT, TreasureHuntContentType.FINAL_CODE);
    }

    @Test
    void revisitingAnAlreadyValidatedFinalCheckpointCreatesItsMissingSixCharacterVaultCode() {
        FinalCheckpointFixture fixture = finalCheckpointFixture();
        when(fixture.generator().generate()).thenReturn("K7MPQ2");
        when(fixture.vaultCodes().tryCreateVaultCode(any(UUID.class), eq(fixture.userId()),
                eq(fixture.pathId()), eq("K7MPQ2"), any(Instant.class))).thenReturn(1);

        assertThat(fixture.service().validateCheckpoint("sub", "KEYPAD", "already-used-code", fixture.pathId()).vaultCode())
                .isEqualTo("K7MPQ2");
    }

    @Test
    void retriesWhenARandomCodeAlreadyBelongsToThePlayer() {
        FinalCheckpointFixture fixture = finalCheckpointFixture();
        when(fixture.generator().generate()).thenReturn("AAAAAA", "Z9K7MP");
        when(fixture.vaultCodes().tryCreateVaultCode(any(UUID.class), eq(fixture.userId()),
                eq(fixture.pathId()), eq("AAAAAA"), any(Instant.class))).thenReturn(0);
        when(fixture.vaultCodes().tryCreateVaultCode(any(UUID.class), eq(fixture.userId()),
                eq(fixture.pathId()), eq("Z9K7MP"), any(Instant.class))).thenReturn(1);

        assertThat(fixture.service().validateCheckpoint("sub", "KEYPAD", "already-used-code", fixture.pathId()).vaultCode())
                .isEqualTo("Z9K7MP");
        verify(fixture.generator(), times(2)).generate();
    }

    private static FinalCheckpointFixture finalCheckpointFixture() {
        UUID userId = UUID.randomUUID();
        UUID pathId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        TreasureHuntPath path = new TreasureHuntPath();
        path.setId(pathId);
        path.setPosition(2);
        TreasureHuntCheckpoint checkpoint = new TreasureHuntCheckpoint();
        checkpoint.setId(checkpointId);
        checkpoint.setPath(path);
        checkpoint.setStepNumber(3);
        TreasureHuntContent finalCode = new TreasureHuntContent();
        finalCode.setType(TreasureHuntContentType.FINAL_CODE);
        checkpoint.addContent(finalCode);
        TreasureHuntSettings settings = new TreasureHuntSettings();
        settings.setActive(true);

        UserRepository users = mock(UserRepository.class);
        TreasureHuntCheckpointRepository checkpoints = mock(TreasureHuntCheckpointRepository.class);
        PlayerCheckpointProgressRepository progress = mock(PlayerCheckpointProgressRepository.class);
        PlayerVaultCodeRepository vaultCodes = mock(PlayerVaultCodeRepository.class);
        PlayerVaultUnlockRepository unlocks = mock(PlayerVaultUnlockRepository.class);
        TreasureHuntVaultRepository vault = mock(TreasureHuntVaultRepository.class);
        TreasureHuntStorageService storage = mock(TreasureHuntStorageService.class);
        TreasureHuntSettingsRepository settingsRepository = mock(TreasureHuntSettingsRepository.class);
        TreasureHuntPathRepository paths = mock(TreasureHuntPathRepository.class);
        VaultCodeGenerator generator = mock(VaultCodeGenerator.class);
        when(settingsRepository.findById(true)).thenReturn(Optional.of(settings));
        when(checkpoints.findByCodeHash(any())).thenReturn(Optional.of(checkpoint));
        when(users.findWithLockByKeycloakSub("sub")).thenReturn(Optional.of(user));
        when(progress.existsByUserIdAndCheckpointId(userId, checkpointId)).thenReturn(true);
        when(vaultCodes.findByUserIdAndPathId(userId, pathId)).thenReturn(Optional.empty());

        TreasureHuntService service = new TreasureHuntService(
                users, checkpoints, progress, vaultCodes, unlocks, vault, storage, settingsRepository, paths,
                generator, mock(AutoChallengeTriggerPublisher.class));

        return new FinalCheckpointFixture(service, vaultCodes, generator, userId, pathId);
    }

    private record FinalCheckpointFixture(TreasureHuntService service,
            PlayerVaultCodeRepository vaultCodes, VaultCodeGenerator generator, UUID userId, UUID pathId) { }

    private record ToolFixture(TreasureHuntService service, UUID pathId, UUID userId, UUID checkpointId,
            PlayerCheckpointProgressRepository progress, TreasureHuntCheckpointRepository checkpoints) { }

    private static ToolFixture toolFixture(TreasureHuntInputTool tool, String canonicalSecret,
            int stepNumber, boolean previousStepDone) {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        TreasureHuntPath path = path(1, "GM");
        TreasureHuntCheckpoint checkpoint = new TreasureHuntCheckpoint();
        checkpoint.setId(UUID.randomUUID());
        checkpoint.setPath(path);
        checkpoint.setStepNumber(stepNumber);
        checkpoint.setInputTool(tool);
        TreasureHuntSettings settings = new TreasureHuntSettings();
        settings.setActive(true);

        UserRepository users = mock(UserRepository.class);
        TreasureHuntCheckpointRepository checkpoints = mock(TreasureHuntCheckpointRepository.class);
        PlayerCheckpointProgressRepository progress = mock(PlayerCheckpointProgressRepository.class);
        TreasureHuntSettingsRepository settingsRepository = mock(TreasureHuntSettingsRepository.class);
        when(settingsRepository.findById(true)).thenReturn(Optional.of(settings));
        when(users.findWithLockByKeycloakSub("sub")).thenReturn(Optional.of(user));
        when(checkpoints.findByCodeHash(any())).thenReturn(Optional.empty());
        when(checkpoints.findByCodeHash(TreasureHuntService.hash(canonicalSecret)))
                .thenReturn(Optional.of(checkpoint));
        when(progress.existsByUserIdAndCheckpointId(userId, checkpoint.getId())).thenReturn(false);
        when(progress.existsByUserIdAndCheckpointPathIdAndCheckpointStepNumber(
                userId, path.getId(), stepNumber - 1)).thenReturn(previousStepDone);

        TreasureHuntService service = new TreasureHuntService(
                users, checkpoints, progress, mock(PlayerVaultCodeRepository.class),
                mock(PlayerVaultUnlockRepository.class), mock(TreasureHuntVaultRepository.class),
                mock(TreasureHuntStorageService.class), settingsRepository,
                mock(TreasureHuntPathRepository.class), mock(VaultCodeGenerator.class),
                mock(AutoChallengeTriggerPublisher.class));
        return new ToolFixture(service, path.getId(), userId, checkpoint.getId(), progress, checkpoints);
    }
}
