package fr.insastronaute.webapi.treasurehunt.service;

import fr.insastronaute.webapi.article.repository.ArticleRepository;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntCheckpoint;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntContent;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntContentType;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntInputTool;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntPath;
import fr.insastronaute.webapi.treasurehunt.dto.AdminCheckpointRequest;
import fr.insastronaute.webapi.treasurehunt.dto.AdminCheckpointResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntActivationStatusResponse;
import fr.insastronaute.webapi.treasurehunt.repository.PlayerCheckpointProgressRepository;
import fr.insastronaute.webapi.treasurehunt.repository.PlayerVaultCodeRepository;
import fr.insastronaute.webapi.treasurehunt.repository.PlayerVaultUnlockRepository;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntCheckpointRepository;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntContentRepository;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntPathRepository;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntSettingsRepository;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntVaultRepository;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntSettings;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TreasureHuntAdminServiceTest {
    // Les etapes deja en base n'ont pas d'outil : elles attendent le clavier, comme avant.
    @Test
    void aCheckpointDefaultsToTheKeypad() {
        assertThat(new TreasureHuntCheckpoint().getInputTool()).isEqualTo(TreasureHuntInputTool.KEYPAD);
    }

    @Test
    void createsAnEmptyCheckpointBeforeItsContentsAreConfigured() {
        TreasureHuntPathRepository paths = mock(TreasureHuntPathRepository.class);
        TreasureHuntCheckpointRepository checkpoints = mock(TreasureHuntCheckpointRepository.class);
        UUID pathId = UUID.randomUUID();
        TreasureHuntPath path = new TreasureHuntPath(); path.setId(pathId);
        when(paths.findById(pathId)).thenReturn(Optional.of(path));
        when(checkpoints.countByPathId(pathId)).thenReturn(0);
        when(checkpoints.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TreasureHuntAdminService service = new TreasureHuntAdminService(paths, checkpoints, mock(TreasureHuntVaultRepository.class),
                mock(TreasureHuntStorageService.class), mock(DepartmentRepository.class));

        assertThat(service.createCheckpoint(pathId, new AdminCheckpointRequest("INFO-001", null)))
                .extracting(AdminCheckpointResponse::stepNumber, AdminCheckpointResponse::code)
                .containsExactly(1, "INFO-001");
    }

    @Test
    void rejectsACheckpointCodeThatIsAlreadyUsed() {
        TreasureHuntPathRepository paths = mock(TreasureHuntPathRepository.class);
        TreasureHuntCheckpointRepository checkpoints = mock(TreasureHuntCheckpointRepository.class);
        UUID pathId = UUID.randomUUID();
        TreasureHuntPath path = new TreasureHuntPath();
        path.setId(pathId);
        TreasureHuntCheckpoint existingCheckpoint = new TreasureHuntCheckpoint();
        existingCheckpoint.setId(UUID.randomUUID());
        when(paths.findById(pathId)).thenReturn(Optional.of(path));
        when(checkpoints.countByPathId(pathId)).thenReturn(0);
        when(checkpoints.findByCodeHash(any(byte[].class))).thenReturn(Optional.of(existingCheckpoint));
        TreasureHuntAdminService service = new TreasureHuntAdminService(paths, checkpoints, mock(TreasureHuntVaultRepository.class),
                mock(TreasureHuntStorageService.class), mock(DepartmentRepository.class));

        assertThatThrownBy(() -> service.createCheckpoint(pathId, new AdminCheckpointRequest("TEST1", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Ce code est déjà utilisé.");
    }

    @Test
    void rejectsChangingACheckpointCodeToOneAlreadyUsedByAnotherCheckpoint() {
        TreasureHuntPathRepository paths = mock(TreasureHuntPathRepository.class);
        TreasureHuntCheckpointRepository checkpoints = mock(TreasureHuntCheckpointRepository.class);
        TreasureHuntCheckpoint checkpoint = new TreasureHuntCheckpoint();
        checkpoint.setId(UUID.randomUUID());
        TreasureHuntCheckpoint existingCheckpoint = new TreasureHuntCheckpoint();
        existingCheckpoint.setId(UUID.randomUUID());
        when(checkpoints.findById(checkpoint.getId())).thenReturn(Optional.of(checkpoint));
        when(checkpoints.findByCodeHash(any(byte[].class))).thenReturn(Optional.of(existingCheckpoint));
        TreasureHuntAdminService service = new TreasureHuntAdminService(paths, checkpoints, mock(TreasureHuntVaultRepository.class),
                mock(TreasureHuntStorageService.class), mock(DepartmentRepository.class));

        assertThatThrownBy(() -> service.updateCheckpoint(checkpoint.getId(), new AdminCheckpointRequest("TEST1", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Ce code est déjà utilisé.");
    }

    @Test
    void updatesAndReturnsTheCheckpointCode() {
        TreasureHuntPathRepository paths = mock(TreasureHuntPathRepository.class);
        TreasureHuntCheckpointRepository checkpoints = mock(TreasureHuntCheckpointRepository.class);
        TreasureHuntPath path = new TreasureHuntPath();
        path.setId(UUID.randomUUID());
        TreasureHuntCheckpoint checkpoint = new TreasureHuntCheckpoint();
        checkpoint.setId(UUID.randomUUID());
        checkpoint.setPath(path);
        checkpoint.setStepNumber(2);
        checkpoint.setCode("OLD-CODE");
        when(checkpoints.findById(checkpoint.getId())).thenReturn(Optional.of(checkpoint));
        when(checkpoints.findByCodeHash(any(byte[].class))).thenReturn(Optional.empty());
        TreasureHuntAdminService service = new TreasureHuntAdminService(paths, checkpoints,
                mock(TreasureHuntVaultRepository.class), mock(TreasureHuntStorageService.class),
                mock(DepartmentRepository.class));

        AdminCheckpointResponse response = service.updateCheckpoint(
                checkpoint.getId(), new AdminCheckpointRequest(" new-code ", null));

        assertThat(response.code()).isEqualTo("new-code");
        assertThat(checkpoint.getCode()).isEqualTo("new-code");
    }

    // Le trajet que personne n'exercait, et exactement celui qui etait casse : l'admin
    // compose une melodie << a la francaise >> -- accents, espaces, diese unicode -- et le
    // joueur la valide sous sa forme canonique. Le hash ecrit doit etre celui que le chemin
    // de validation reconstruira, sans quoi l'etape est injouable a vie.
    //
    // Test de service et non d'integration : les deux cotes ne partagent qu'un hash, et le
    // faire circuler par une base et un Keycloak coutait bien plus que ce qu'il prouvait.
    @Test
    void aMelodyWrittenTheFrenchWayByTheAdminValidatesForThePlayer() {
        TreasureHuntPathRepository paths = mock(TreasureHuntPathRepository.class);
        TreasureHuntCheckpointRepository checkpoints = mock(TreasureHuntCheckpointRepository.class);
        UUID pathId = UUID.randomUUID();
        TreasureHuntPath path = new TreasureHuntPath();
        path.setId(pathId);
        when(paths.findById(pathId)).thenReturn(Optional.of(path));
        when(checkpoints.countByPathId(pathId)).thenReturn(0);
        when(checkpoints.findByCodeHash(any(byte[].class))).thenReturn(Optional.empty());
        when(checkpoints.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TreasureHuntAdminService admin = new TreasureHuntAdminService(paths, checkpoints,
                mock(TreasureHuntVaultRepository.class), mock(TreasureHuntStorageService.class),
                mock(DepartmentRepository.class));

        AdminCheckpointResponse created = admin.createCheckpoint(pathId,
                new AdminCheckpointRequest("do ré♯ mi", TreasureHuntInputTool.PIANO));

        // Le panneau garde la forme lisible que l'administrateur a tapee.
        assertThat(created.code()).isEqualTo("do ré♯ mi");
        // Le hash, lui, est canonique. La verification d'unicite est la seule fenetre sur
        // ce que le cote ecriture a reellement hache.
        ArgumentCaptor<byte[]> writtenHash = ArgumentCaptor.forClass(byte[].class);
        verify(checkpoints).findByCodeHash(writtenHash.capture());
        assertThat(writtenHash.getValue()).isEqualTo(TreasureHuntService.hash("DO-RE#-MI"));

        // Et le joueur, qui envoie la forme canonique, franchit l'etape.
        assertThat(playerFacing(writtenHash.getValue(), pathId)
                .validateCheckpoint("sub", "PIANO", "DO-RE#-MI", null).stepNumber()).isEqualTo(1);

        // L'autre sens, celui que ce test n'exercait pas : le joueur ecrit lui aussi
        // librement -- accents, espaces, diese unicode -- et c'est le repli cote lecture qui
        // le fait tomber sur le meme hash. Sans cette moitie, le test ne prouvait que
        // << ecriture francaise -> lecture canonique >>, pas la lecture libre.
        assertThat(playerFacing(writtenHash.getValue(), pathId)
                .validateCheckpoint("sub", "piano", "  do   ré♯  mi ", null).stepNumber()).isEqualTo(1);
    }

    // L'unicite promise est celle du secret, melodies comprises : avec le hash canonique,
    // "DO RE MI" et "do ré mi" designent la meme melodie et doivent entrer en conflit.
    @Test
    void refusesASecondWritingOfAMelodyAlreadyUsed() {
        TreasureHuntPathRepository paths = mock(TreasureHuntPathRepository.class);
        TreasureHuntCheckpointRepository checkpoints = mock(TreasureHuntCheckpointRepository.class);
        UUID pathId = UUID.randomUUID();
        TreasureHuntPath path = new TreasureHuntPath();
        path.setId(pathId);
        TreasureHuntCheckpoint existing = new TreasureHuntCheckpoint();
        existing.setId(UUID.randomUUID());
        when(paths.findById(pathId)).thenReturn(Optional.of(path));
        when(checkpoints.countByPathId(pathId)).thenReturn(0);
        when(checkpoints.findByCodeHash(any(byte[].class))).thenReturn(Optional.empty());
        when(checkpoints.findByCodeHash(TreasureHuntService.hash("DO-RE-MI"))).thenReturn(Optional.of(existing));
        when(checkpoints.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TreasureHuntAdminService service = new TreasureHuntAdminService(paths, checkpoints,
                mock(TreasureHuntVaultRepository.class), mock(TreasureHuntStorageService.class),
                mock(DepartmentRepository.class));

        assertThatThrownBy(() -> service.createCheckpoint(pathId,
                new AdminCheckpointRequest("do ré mi", TreasureHuntInputTool.PIANO)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Ce code est déjà utilisé.");
        // Une autre melodie, elle, passe : le conflit tient a la melodie, pas au piano.
        assertThat(service.createCheckpoint(pathId,
                new AdminCheckpointRequest("do ré fa", TreasureHuntInputTool.PIANO)).code())
                .isEqualTo("do ré fa");
    }

    // Une melodie faite de faux jetons ne doit pas pouvoir etre enregistree : elle serait
    // hachee sous une forme qu'aucun joueur ne peut reproduire. Cote administration, le
    // message est explicite -- il n'y a rien a cacher a celui qui compose l'enigme.
    @Test
    void refusesAMelodyMadeOfTokensThatAreNotNotes() {
        TreasureHuntPathRepository paths = mock(TreasureHuntPathRepository.class);
        TreasureHuntCheckpointRepository checkpoints = mock(TreasureHuntCheckpointRepository.class);
        UUID pathId = UUID.randomUUID();
        TreasureHuntPath path = new TreasureHuntPath();
        path.setId(pathId);
        when(paths.findById(pathId)).thenReturn(Optional.of(path));
        when(checkpoints.countByPathId(pathId)).thenReturn(0);
        TreasureHuntAdminService service = new TreasureHuntAdminService(paths, checkpoints,
                mock(TreasureHuntVaultRepository.class), mock(TreasureHuntStorageService.class),
                mock(DepartmentRepository.class));

        assertThatThrownBy(() -> service.createCheckpoint(pathId,
                new AdminCheckpointRequest("DO H MI", TreasureHuntInputTool.PIANO)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("La mélodie doit être une suite de notes");
    }

    // A la creation, un outil absent vaut KEYPAD : un panneau anterieur a l'etabli n'envoie
    // pas ce champ. A la mise a jour, il vaut << inchange >> -- un panneau reste en cache
    // qui corrige un code ne doit pas ramener silencieusement une etape piano au clavier.
    @Test
    void anAbsentToolMeansKeypadAtCreationAndUnchangedAtUpdate() {
        TreasureHuntPathRepository paths = mock(TreasureHuntPathRepository.class);
        TreasureHuntCheckpointRepository checkpoints = mock(TreasureHuntCheckpointRepository.class);
        UUID pathId = UUID.randomUUID();
        TreasureHuntPath path = new TreasureHuntPath();
        path.setId(pathId);
        TreasureHuntCheckpoint piano = new TreasureHuntCheckpoint();
        piano.setId(UUID.randomUUID());
        piano.setPath(path);
        piano.setStepNumber(2);
        piano.setInputTool(TreasureHuntInputTool.PIANO);
        piano.setCode("do ré mi");
        when(paths.findById(pathId)).thenReturn(Optional.of(path));
        when(checkpoints.countByPathId(pathId)).thenReturn(0);
        when(checkpoints.findById(piano.getId())).thenReturn(Optional.of(piano));
        when(checkpoints.findByCodeHash(any(byte[].class))).thenReturn(Optional.empty());
        when(checkpoints.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TreasureHuntAdminService service = new TreasureHuntAdminService(paths, checkpoints,
                mock(TreasureHuntVaultRepository.class), mock(TreasureHuntStorageService.class),
                mock(DepartmentRepository.class));

        assertThat(service.createCheckpoint(pathId, new AdminCheckpointRequest("GM-1", null)).inputTool())
                .isEqualTo(TreasureHuntInputTool.KEYPAD);
        assertThat(service.updateCheckpoint(piano.getId(), new AdminCheckpointRequest("do ré fa", null)).inputTool())
                .isEqualTo(TreasureHuntInputTool.PIANO);
    }

    // Une etape dont le secret n'est pas repliable par son outil empeche d'activer la chasse :
    // elle est injouable, et rien d'autre ne le signalerait avant le premier joueur.
    @Test
    void refusesToActivateWhenAStepSecretCannotBeReadByItsOwnTool() {
        assertThat(activationViolations("DO H MI", TreasureHuntInputTool.PIANO))
                .contains("Chaque étape de la voie GM doit avoir un code que son outil de saisie sait lire.");
    }

    // Et ce n'est pas une contrainte de longueur : une melodie d'une seule note est courte,
    // epuisable, et parfaitement valide -- c'est un choix de composition, pas une erreur.
    @Test
    void aSingleNoteMelodyDoesNotPreventActivation() {
        assertThat(activationViolations("DO", TreasureHuntInputTool.PIANO))
                .doesNotContain("Chaque étape de la voie GM doit avoir un code que son outil de saisie sait lire.");
    }

    // Le hachage d'une melodie a change : toute etape piano enregistree avant ce changement
    // porte une empreinte calculee sur sa chaine brute, que le joueur ne peut plus
    // reproduire, et reste injouable a vie. Un hash ne s'inverse pas, mais `code` est stocke
    // en clair : la regle re-hache son repli et compare.
    @Test
    void refusesToActivateWhenAStepCarriesAnOutdatedHash() {
        assertThat(activationViolations("do re mi", TreasureHuntInputTool.PIANO,
                TreasureHuntService.hash("do re mi")))
                .contains("L'étape 1 de la voie GM porte une empreinte périmée :"
                        + " réenregistrez son code pour la rendre jouable.");
    }

    // Et elle ne se declenche pas sur une etape clavier legitime : la normalisation d'un
    // outil non-piano est exactement le trim et les majuscules sous lesquels son code a
    // toujours ete hache, donc l'empreinte concorde.
    @Test
    void leavesALegitimateKeypadStepAlone() {
        assertThat(activationViolations("gm-1", TreasureHuntInputTool.KEYPAD))
                .noneMatch(violation -> violation.contains("empreinte périmée"));
    }

    // Une etape sans code est deja nommee par la violation qui exige un code, et une etape
    // que son outil ne sait pas lire par celle qui l'exige lisible : la regle de l'empreinte
    // ne redit ni l'une ni l'autre.
    @Test
    void doesNotRepeatViolationsAlreadyNamedElsewhere() {
        assertThat(activationViolations(null, TreasureHuntInputTool.KEYPAD))
                .contains("Chaque étape de la voie GM doit avoir un code.")
                .noneMatch(violation -> violation.contains("empreinte périmée"));
        assertThat(activationViolations("DO H MI", TreasureHuntInputTool.PIANO))
                .noneMatch(violation -> violation.contains("empreinte périmée"));
    }

    // Un telegraphe se configure a l'avance -- le choix offert a l'administrateur ne cache
    // aucun des sept outils -- mais la chasse ne part pas avec : aucun joueur ne pourrait
    // franchir cette etape, puisque le front ne dessine pas encore l'outil qu'elle attend.
    @Test
    void refusesToActivateWhenAStepExpectsAToolThePlayerFrontCannotShow() {
        assertThat(activationViolations("... --- ...", TreasureHuntInputTool.TELEGRAPH))
                .contains("L'étape 1 de la voie GM attend l'outil TELEGRAPH, que le front joueur"
                        + " ne sait pas encore afficher : elle serait injouable.");
    }

    // Et la garde suit l'ensemble declare cote domaine, pas une liste recopiee ici : les deux
    // outils que le front implemente aujourd'hui passent, les cinq autres non.
    @Test
    void letsThroughEveryToolThePlayerFrontImplements() {
        assertThat(TreasureHuntInputTool.IMPLEMENTED_BY_THE_PLAYER_FRONT)
                .containsExactlyInAnyOrder(TreasureHuntInputTool.KEYPAD, TreasureHuntInputTool.PIANO);
        for (TreasureHuntInputTool tool : TreasureHuntInputTool.values()) {
            String code = tool == TreasureHuntInputTool.PIANO ? "DO RE MI" : "GM-1";
            assertThat(activationViolations(code, tool))
                    .as("outil %s", tool)
                    .filteredOn(violation -> violation.contains("ne sait pas encore afficher"))
                    .hasSize(tool.isImplementedByThePlayerFront() ? 0 : 1);
        }
    }

    // La garde d'activate() n'etait prouvee que par activationStatus(), qui ne fait que lire :
    // un refactor qui journaliserait les violations puis activerait quand meme y passerait
    // au vert. Ici on verifie le fait, pas seulement le retour -- rien n'est ecrit.
    @Test
    void activateDoesNotSaveSettingsWhenThereAreViolations() {
        TreasureHuntPathRepository paths = mock(TreasureHuntPathRepository.class);
        TreasureHuntCheckpointRepository checkpoints = mock(TreasureHuntCheckpointRepository.class);
        TreasureHuntContentRepository contents = mock(TreasureHuntContentRepository.class);
        TreasureHuntSettingsRepository settingsRepository = mock(TreasureHuntSettingsRepository.class);
        TreasureHuntPath path = new TreasureHuntPath();
        path.setId(UUID.randomUUID());
        path.setName("GM");
        List<TreasureHuntCheckpoint> steps = List.of(
                step(path, 1, "DO H MI", TreasureHuntInputTool.PIANO),
                step(path, 2, "GM-2", TreasureHuntInputTool.KEYPAD),
                step(path, 3, "GM-3", TreasureHuntInputTool.KEYPAD));
        TreasureHuntContent finalCode = new TreasureHuntContent();
        finalCode.setType(TreasureHuntContentType.FINAL_CODE);
        when(paths.findAllByOrderByPositionAsc()).thenReturn(List.of(path));
        when(checkpoints.findByPathIdOrderByStepNumberAsc(path.getId())).thenReturn(steps);
        when(contents.findByCheckpointIdOrderByPositionAsc(steps.get(2).getId())).thenReturn(List.of(finalCode));
        when(contents.countByVaultId(true)).thenReturn(1L);
        when(settingsRepository.findById(true)).thenReturn(Optional.empty());
        TreasureHuntAdminService service = new TreasureHuntAdminService(paths, checkpoints,
                mock(TreasureHuntVaultRepository.class), mock(TreasureHuntStorageService.class),
                mock(DepartmentRepository.class), contents, mock(ArticleRepository.class), settingsRepository);

        TreasureHuntActivationStatusResponse response = service.activate();

        assertThat(response.active()).isFalse();
        assertThat(response.violations()).isNotEmpty();
        // Le point qui distingue << refuse >> de << active puis se plaint >>.
        verify(settingsRepository, never()).save(any());
    }

    // Le symetrique : sans violation, activate() ecrit bel et bien l'etat actif.
    @Test
    void activateSavesActiveSettingsWhenThereAreNoViolations() {
        TreasureHuntPathRepository paths = mock(TreasureHuntPathRepository.class);
        TreasureHuntCheckpointRepository checkpoints = mock(TreasureHuntCheckpointRepository.class);
        TreasureHuntContentRepository contents = mock(TreasureHuntContentRepository.class);
        TreasureHuntSettingsRepository settingsRepository = mock(TreasureHuntSettingsRepository.class);
        List<TreasureHuntPath> allPaths = new java.util.ArrayList<>();
        for (int index = 0; index < 6; index++) {
            TreasureHuntPath path = new TreasureHuntPath();
            path.setId(UUID.randomUUID());
            path.setName("Voie " + index);
            path.setDepartment(new Department());
            List<TreasureHuntCheckpoint> steps = List.of(
                    step(path, 1, "GM-1", TreasureHuntInputTool.KEYPAD),
                    step(path, 2, "GM-2", TreasureHuntInputTool.KEYPAD),
                    step(path, 3, "GM-3", TreasureHuntInputTool.KEYPAD));
            when(checkpoints.findByPathIdOrderByStepNumberAsc(path.getId())).thenReturn(steps);
            TreasureHuntContent finalCode = new TreasureHuntContent();
            finalCode.setType(TreasureHuntContentType.FINAL_CODE);
            when(contents.findByCheckpointIdOrderByPositionAsc(steps.get(2).getId())).thenReturn(List.of(finalCode));
            allPaths.add(path);
        }
        when(paths.findAllByOrderByPositionAsc()).thenReturn(allPaths);
        when(contents.countByVaultId(true)).thenReturn(1L);
        when(settingsRepository.findById(true)).thenReturn(Optional.empty());
        when(settingsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TreasureHuntAdminService service = new TreasureHuntAdminService(paths, checkpoints,
                mock(TreasureHuntVaultRepository.class), mock(TreasureHuntStorageService.class),
                mock(DepartmentRepository.class), contents, mock(ArticleRepository.class), settingsRepository);

        TreasureHuntActivationStatusResponse response = service.activate();

        assertThat(response.active()).isTrue();
        assertThat(response.violations()).isEmpty();
        ArgumentCaptor<TreasureHuntSettings> savedSettings = ArgumentCaptor.forClass(TreasureHuntSettings.class);
        verify(settingsRepository).save(savedSettings.capture());
        assertThat(savedSettings.getValue().isActive()).isTrue();
    }

    /** Les trois etapes sont enregistrees sous la regle de hachage courante. */
    private static List<String> activationViolations(String firstCode, TreasureHuntInputTool firstTool) {
        return activationViolations(firstCode, firstTool, currentRuleHash(firstTool, firstCode));
    }

    private static List<String> activationViolations(String firstCode, TreasureHuntInputTool firstTool,
            byte[] firstCodeHash) {
        TreasureHuntPathRepository paths = mock(TreasureHuntPathRepository.class);
        TreasureHuntCheckpointRepository checkpoints = mock(TreasureHuntCheckpointRepository.class);
        TreasureHuntContentRepository contents = mock(TreasureHuntContentRepository.class);
        TreasureHuntSettingsRepository settingsRepository = mock(TreasureHuntSettingsRepository.class);
        TreasureHuntPath path = new TreasureHuntPath();
        path.setId(UUID.randomUUID());
        path.setName("GM");
        List<TreasureHuntCheckpoint> steps = List.of(
                step(path, 1, firstCode, firstTool, firstCodeHash),
                step(path, 2, "GM-2", TreasureHuntInputTool.KEYPAD),
                step(path, 3, "GM-3", TreasureHuntInputTool.KEYPAD));
        TreasureHuntContent finalCode = new TreasureHuntContent();
        finalCode.setType(TreasureHuntContentType.FINAL_CODE);
        when(paths.findAllByOrderByPositionAsc()).thenReturn(List.of(path));
        when(checkpoints.findByPathIdOrderByStepNumberAsc(path.getId())).thenReturn(steps);
        when(contents.findByCheckpointIdOrderByPositionAsc(steps.get(2).getId())).thenReturn(List.of(finalCode));
        when(contents.countByVaultId(true)).thenReturn(1L);
        when(settingsRepository.findById(true)).thenReturn(Optional.empty());

        return new TreasureHuntAdminService(paths, checkpoints, mock(TreasureHuntVaultRepository.class),
                mock(TreasureHuntStorageService.class), mock(DepartmentRepository.class), contents,
                mock(ArticleRepository.class), settingsRepository).activationStatus().violations();
    }

    private static TreasureHuntCheckpoint step(TreasureHuntPath path, int stepNumber, String code,
            TreasureHuntInputTool tool) {
        return step(path, stepNumber, code, tool, currentRuleHash(tool, code));
    }

    private static TreasureHuntCheckpoint step(TreasureHuntPath path, int stepNumber, String code,
            TreasureHuntInputTool tool, byte[] codeHash) {
        TreasureHuntCheckpoint checkpoint = new TreasureHuntCheckpoint();
        checkpoint.setId(UUID.randomUUID());
        checkpoint.setPath(path);
        checkpoint.setStepNumber(stepNumber);
        checkpoint.setCode(code);
        checkpoint.setInputTool(tool);
        checkpoint.setCodeHash(codeHash);
        return checkpoint;
    }

    /** L'empreinte qu'un enregistrement d'aujourd'hui produirait, ou null s'il n'y en a pas. */
    private static byte[] currentRuleHash(TreasureHuntInputTool tool, String code) {
        return TreasureHuntSecretCodec.normalize(tool, code).map(TreasureHuntService::hash).orElse(null);
    }

    /** Le cote joueur, arme du seul hash que le cote ecriture a produit. */
    private static TreasureHuntService playerFacing(byte[] codeHash, UUID pathId) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        TreasureHuntPath path = new TreasureHuntPath();
        path.setId(pathId);
        TreasureHuntCheckpoint checkpoint = new TreasureHuntCheckpoint();
        checkpoint.setId(UUID.randomUUID());
        checkpoint.setPath(path);
        checkpoint.setStepNumber(1);
        checkpoint.setInputTool(TreasureHuntInputTool.PIANO);
        TreasureHuntSettings settings = new TreasureHuntSettings();
        settings.setActive(true);

        UserRepository users = mock(UserRepository.class);
        TreasureHuntCheckpointRepository checkpoints = mock(TreasureHuntCheckpointRepository.class);
        TreasureHuntSettingsRepository settingsRepository = mock(TreasureHuntSettingsRepository.class);
        when(settingsRepository.findById(true)).thenReturn(Optional.of(settings));
        when(users.findWithLockByKeycloakSub("sub")).thenReturn(Optional.of(user));
        when(checkpoints.findByCodeHash(any())).thenReturn(Optional.empty());
        when(checkpoints.findByCodeHash(codeHash)).thenReturn(Optional.of(checkpoint));

        return new TreasureHuntService(users, checkpoints, mock(PlayerCheckpointProgressRepository.class),
                mock(PlayerVaultCodeRepository.class), mock(PlayerVaultUnlockRepository.class),
                mock(TreasureHuntVaultRepository.class), mock(TreasureHuntStorageService.class), settingsRepository,
                mock(TreasureHuntPathRepository.class), mock(VaultCodeGenerator.class),
                mock(AutoChallengeTriggerPublisher.class));
    }
}
