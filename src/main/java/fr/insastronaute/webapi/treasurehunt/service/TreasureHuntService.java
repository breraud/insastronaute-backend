package fr.insastronaute.webapi.treasurehunt.service;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ForbiddenException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.treasurehunt.domain.PlayerCheckpointProgress;
import fr.insastronaute.webapi.treasurehunt.domain.PlayerVaultCode;
import fr.insastronaute.webapi.treasurehunt.domain.PlayerVaultUnlock;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntCheckpoint;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntContent;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntContentType;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntInputTool;
import fr.insastronaute.webapi.treasurehunt.dto.CheckpointValidationResponse;
import fr.insastronaute.webapi.treasurehunt.dto.PlayerPathProgressResponse;
import fr.insastronaute.webapi.treasurehunt.dto.PlayerTreasureHuntProgressResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntContentResponse;
import fr.insastronaute.webapi.treasurehunt.dto.ValidatedCheckpointResponse;
import fr.insastronaute.webapi.treasurehunt.dto.ValidatedCheckpointsResponse;
import fr.insastronaute.webapi.treasurehunt.dto.VaultCodeResponse;
import fr.insastronaute.webapi.treasurehunt.dto.VaultCodesResponse;
import fr.insastronaute.webapi.treasurehunt.dto.VaultUnlockResponse;
import fr.insastronaute.webapi.treasurehunt.repository.PlayerCheckpointProgressRepository;
import fr.insastronaute.webapi.treasurehunt.repository.PlayerVaultCodeRepository;
import fr.insastronaute.webapi.treasurehunt.repository.PlayerVaultUnlockRepository;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntCheckpointRepository;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntPathRepository;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntVaultRepository;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntSettingsRepository;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntSettings;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TreasureHuntService {
    private static final int VAULT_CODE_GENERATION_ATTEMPTS = 32;
    public static final String REFUSAL = "Rien ne s'ouvre. Ce code est erroné — ou pas encore disponible.";

    private final UserRepository userRepository;
    private final TreasureHuntCheckpointRepository checkpointRepository;
    private final PlayerCheckpointProgressRepository progressRepository;
    private final PlayerVaultCodeRepository vaultCodeRepository;
    private final PlayerVaultUnlockRepository vaultUnlockRepository;
    private final TreasureHuntVaultRepository vaultRepository;
    private final TreasureHuntStorageService storageService;
    private final TreasureHuntSettingsRepository settingsRepository;
    private final TreasureHuntPathRepository pathRepository;
    private final VaultCodeGenerator vaultCodeGenerator;
    private final AutoChallengeTriggerPublisher autoChallengeTriggerPublisher;

    public TreasureHuntService(UserRepository userRepository, TreasureHuntCheckpointRepository checkpointRepository,
            PlayerCheckpointProgressRepository progressRepository, PlayerVaultCodeRepository vaultCodeRepository,
            PlayerVaultUnlockRepository vaultUnlockRepository, TreasureHuntVaultRepository vaultRepository,
            TreasureHuntStorageService storageService, TreasureHuntSettingsRepository settingsRepository,
            TreasureHuntPathRepository pathRepository, VaultCodeGenerator vaultCodeGenerator,
            AutoChallengeTriggerPublisher autoChallengeTriggerPublisher) {
        this.userRepository = userRepository;
        this.checkpointRepository = checkpointRepository;
        this.progressRepository = progressRepository;
        this.vaultCodeRepository = vaultCodeRepository;
        this.vaultUnlockRepository = vaultUnlockRepository;
        this.vaultRepository = vaultRepository;
        this.storageService = storageService;
        this.settingsRepository = settingsRepository;
        this.pathRepository = pathRepository;
        this.vaultCodeGenerator = vaultCodeGenerator;
        this.autoChallengeTriggerPublisher = autoChallengeTriggerPublisher;
    }

    @Transactional
    public CheckpointValidationResponse validateCheckpoint(String keycloakSub, String tool,
            String submittedSecret, UUID pathId) {
        requireActive();
        // Le joueur est charge avant la recherche du secret, et non apres : un secret
        // inconnu et un secret reconnu partent tous deux d'au moins cette requete.
        AppUser user = userRepository.findWithLockByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        TreasureHuntInputTool declaredTool = toolOf(tool);
        // Il n'existe que deux normalisations : celle du piano, et celle des six autres
        // outils. Les deux sont calculees et cherchees a chaque fois, quel que soit l'outil
        // declare ; seul le resultat de la normalisation de l'outil declare est ensuite
        // retenu. Le nombre de recherches de secret ne depend donc pas de l'outil soumis :
        // sans cela, chercher la seule forme du piano quand PIANO est declare aurait suffi a
        // reconnaitre le piano au chronometre.
        Optional<String> melody = TreasureHuntSecretCodec.normalize(TreasureHuntInputTool.PIANO, submittedSecret);
        Optional<String> plain = TreasureHuntSecretCodec.normalize(TreasureHuntInputTool.KEYPAD, submittedSecret);
        Optional<TreasureHuntCheckpoint> melodyMatch = melody
                .flatMap(secret -> checkpointRepository.findByCodeHash(hash(secret)));
        Optional<TreasureHuntCheckpoint> plainMatch = plain
                .flatMap(secret -> checkpointRepository.findByCodeHash(hash(secret)));
        Optional<TreasureHuntCheckpoint> declaredMatch =
                declaredTool == TreasureHuntInputTool.PIANO ? melodyMatch : plainMatch;

        // Les deux requetes de progression qui suivent portent sur la premiere des deux
        // normalisations qui trouve quelque chose, et non sur la seule branche retenue : leur
        // nombre ne depend donc que du texte soumis, jamais de l'outil qui l'a soumis. Sans
        // cela une etape piano "DO RE MI" soumise en PIANO coutait deux requetes de plus que
        // le meme texte soumis en KEYPAD ou par un leurre -- la branche retenue etait vide et
        // le refus tombait avant elles -- et le chronometrage isolait le piano.
        //
        // La branche retenue est essayee d'abord, pour que les decisions qui suivent -- outil,
        // pathId, idempotence, etape precedente -- portent bien sur l'etape reellement
        // retenue. Quand elle est vide, ces deux requetes sont depensees sur l'etape que
        // l'autre normalisation a trouvee et leur resultat est jete : c'est le refus commun
        // qui tombe juste apres, au meme prix.
        //
        // Ce n'est pas vrai du secret qu'aucune des deux normalisations ne trouve : il est
        // refuse plus haut et moins cher, mais il n'apprend rien sur l'outil -- seulement que
        // ce secret n'existe sous aucune forme.
        //
        // Ce qui reste inegal ne porte sur aucun outil : un texte que le piano ne sait pas
        // replier coute une recherche de moins qu'un texte qu'il replie. Cet ecart ne
        // depend que du texte soumis -- le meme par tous les outils -- et n'apprend rien de
        // plus que la liste des douze notes, que le piano affiche deja.
        //
        // Un seul reste depend encore de l'outil, et il demande une configuration que
        // l'administration ne produit pas d'elle-meme : si les deux normalisations trouvent
        // deux etapes differentes -- donc deux secrets dont l'un est le repli de l'autre --,
        // c'est la branche retenue qui decide laquelle est sondee, et une etape 1 coute une
        // requete de moins qu'une etape 2 ou 3. L'ecart vaut alors une requete.
        TreasureHuntCheckpoint probed = declaredMatch.or(() -> melodyMatch.or(() -> plainMatch))
                .orElseThrow(() -> new BadRequestException(REFUSAL));
        boolean alreadyValidated = progressRepository.existsByUserIdAndCheckpointId(user.getId(), probed.getId());
        boolean previousStepMissing = probed.getStepNumber() > 1 && !progressRepository
                .existsByUserIdAndCheckpointPathIdAndCheckpointStepNumber(
                        user.getId(), probed.getPath().getId(), probed.getStepNumber() - 1);

        TreasureHuntCheckpoint checkpoint = declaredMatch.orElseThrow(() -> new BadRequestException(REFUSAL));
        if (checkpoint.getInputTool() != declaredTool) throw new BadRequestException(REFUSAL);
        if (pathId != null && !checkpoint.getPath().getId().equals(pathId)) throw new BadRequestException(REFUSAL);
        if (alreadyValidated) {
            return response(checkpoint, hasFinalCode(checkpoint) ? createVaultCode(user, checkpoint) : null);
        }
        if (previousStepMissing) throw new BadRequestException(REFUSAL);

        PlayerCheckpointProgress progress = new PlayerCheckpointProgress();
        progress.setUser(user);
        progress.setCheckpoint(checkpoint);
        progressRepository.save(progress);
        autoChallengeTriggerPublisher.publish(progress.getUser().getId(), AutoChallengeTrigger.TREASURE_CHECKPOINT_VALIDATED);
        return response(checkpoint, hasFinalCode(checkpoint) ? createVaultCode(user, checkpoint) : null);
    }

    /**
     * L'outil declare par le joueur. L'asymetrie des deux cas limites est voulue, et c'est
     * elle qui porte toute la compatibilite descendante de cet endpoint :
     *
     * - aucun outil envoye (null) rend KEYPAD. Un client anterieur a l'etabli ne connait
     *   que le clavier, et ses saisies doivent continuer de valider telles quelles.
     * - un nom d'outil inconnu rend null, qu'aucune etape ne peut egaler -- getInputTool()
     *   n'est jamais null. Ce n'est pas une erreur : c'est un outil qu'aucune etape
     *   n'attend, exactement comme un leurre, et il recoit donc le refus commun. Un 400 de
     *   deserialisation designerait l'outil attendu aussi surement qu'un message explicite,
     *   et ajouter un leurre au front ne doit demander aucun deploiement du backend.
     */
    private static TreasureHuntInputTool toolOf(String tool) {
        if (tool == null) return TreasureHuntInputTool.KEYPAD;
        try {
            return TreasureHuntInputTool.valueOf(tool.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public PlayerTreasureHuntProgressResponse getProgress(String keycloakSub) {
        requireActive();
        AppUser user = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        Map<UUID, PlayerVaultCode> vaultCodesByPathId = vaultCodeRepository
                .findByUserIdOrderByPathDepartmentShortNameAsc(user.getId()).stream()
                .collect(Collectors.toMap(code -> code.getPath().getId(), Function.identity()));
        // Par nom de departement, pas par position : l'ordre du tableau rendu est lui
        // aussi une reponse, et celui des voies est la combinaison du coffre.
        return new PlayerTreasureHuntProgressResponse(pathRepository.findAllByOrderByDepartmentShortNameAsc().stream()
                .map(path -> new PlayerPathProgressResponse(
                        path.getId(),
                        path.getDepartment().getShortName(),
                        progressRepository.countByUserIdAndCheckpointPathId(user.getId(), path.getId()),
                        vaultCodesByPathId.containsKey(path.getId()) ? vaultCodesByPathId.get(path.getId()).getCode() : null
                ))
                .toList());
    }

    /**
     * Ce que le joueur a deja gagne, rouvrable a volonte.
     *
     * Le contenu d'une etape ne vivait que dans la reponse a la validation du code :
     * la modale fermee, il etait perdu, et le code est reste sur la balise. Relire un
     * point deja franchi ne redonne donc rien de neuf -- seulement ce qui avait deja
     * ete montre une fois.
     */
    @Transactional(readOnly = true)
    public ValidatedCheckpointsResponse getValidatedCheckpoints(String keycloakSub) {
        requireActive();
        AppUser user = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        return new ValidatedCheckpointsResponse(progressRepository.findValidatedCheckpoints(user.getId()).stream()
                .map(checkpoint -> new ValidatedCheckpointResponse(
                        checkpoint.getPath().getId(),
                        checkpoint.getStepNumber(),
                        checkpoint.getContents().stream().map(this::contentResponse).toList()))
                .toList());
    }

    @Transactional(readOnly = true)
    public VaultCodesResponse getVaultCodes(String keycloakSub) {
        requireActive();
        AppUser user = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        return new VaultCodesResponse(vaultCodeRepository.findByUserIdOrderByPathDepartmentShortNameAsc(user.getId()).stream()
                .map(code -> new VaultCodeResponse(code.getPath().getId(), code.getCode()))
                .toList());
    }

    @Transactional
    public VaultUnlockResponse unlockVault(String keycloakSub, List<String> submittedCodes) {
        requireActive();
        AppUser user = userRepository.findWithLockByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        // Le seul endroit ou l'ordre des voies sert : la comparaison. Il ne sort pas d'ici.
        List<PlayerVaultCode> issuedCodes = vaultCodeRepository.findByUserIdOrderByPathPositionAsc(user.getId());
        if (submittedCodes == null || issuedCodes.size() != 6 || submittedCodes.size() != 6
                || !issuedCodes.stream().map(PlayerVaultCode::getCode).toList().equals(submittedCodes)) {
            throw new BadRequestException("Combinaison du coffre incorrecte.");
        }
        if (vaultUnlockRepository.findByUserId(user.getId()).isEmpty()) {
            PlayerVaultUnlock unlock = new PlayerVaultUnlock();
            unlock.setUser(user);
            vaultUnlockRepository.save(unlock);
        }
        List<TreasureHuntContent> contents = vaultRepository.findById(true)
                .orElseThrow(() -> new NotFoundException("La récompense finale n'est pas configurée."))
                .getContents();
        return new VaultUnlockResponse(contents.stream().map(this::contentResponse).toList());
    }

    private CheckpointValidationResponse response(TreasureHuntCheckpoint checkpoint, String vaultCode) {
        return new CheckpointValidationResponse(checkpoint.getPath().getId(),
                checkpoint.getStepNumber(), checkpoint.getContents().stream().map(this::contentResponse).toList(), vaultCode);
    }

    private String createVaultCode(AppUser user, TreasureHuntCheckpoint checkpoint) {
        UUID pathId = checkpoint.getPath().getId();
        Optional<PlayerVaultCode> existingCode = vaultCodeRepository.findByUserIdAndPathId(user.getId(), pathId);
        if (existingCode.isPresent()) return existingCode.get().getCode();

        for (int attempt = 0; attempt < VAULT_CODE_GENERATION_ATTEMPTS; attempt++) {
            String candidate = vaultCodeGenerator.generate();
            int inserted = vaultCodeRepository.tryCreateVaultCode(
                    UUID.randomUUID(), user.getId(), pathId, candidate, fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
            if (inserted == 1) return candidate;

            existingCode = vaultCodeRepository.findByUserIdAndPathId(user.getId(), pathId);
            if (existingCode.isPresent()) return existingCode.get().getCode();
        }
        throw new IllegalStateException("Impossible de générer un code de coffre unique pour ce joueur.");
    }

    private boolean hasFinalCode(TreasureHuntCheckpoint checkpoint) { return checkpoint.getContents().stream().anyMatch(content -> content.getType() == TreasureHuntContentType.FINAL_CODE); }
    private TreasureHuntContentResponse contentResponse(TreasureHuntContent content) {
        return new TreasureHuntContentResponse(content.getType(), content.getText(),
                content.getType() == TreasureHuntContentType.MEDIA ? storageService.createReadUrl(content.getStoragePath()) : null,
                content.getArticleId());
    }

    // Package-prive : TreasureHuntServiceTest, dans le meme paquet, en a besoin pour
    // armer un depot simule sans qu'un oracle de hachage soit public en production.
    static byte[] hash(String code) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponible", exception);
        }
    }

    /**
     * Cachée, la chasse n'est pas seulement absente du front : elle est fermée.
     * Un lien gardé en favori ne doit pas rouvrir ce qu'on a décidé de retirer.
     */
    @Transactional(readOnly = true)
    public boolean isVisible() {
        return !settingsRepository.findById(true).map(TreasureHuntSettings::isHidden).orElse(false);
    }

    private void requireActive() {
        TreasureHuntSettings settings = settingsRepository.findById(true).orElse(null);
        if (settings != null && settings.isActive() && !settings.isHidden()) return;
        throw new ForbiddenException("La chasse au trésor n'est pas encore active.");
    }
}
