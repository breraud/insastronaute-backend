package fr.insastronaute.webapi.treasurehunt.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.article.repository.ArticleRepository;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntContent;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntContentType;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntPath;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntCheckpoint;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntInputTool;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntVault;
import fr.insastronaute.webapi.treasurehunt.dto.AdminCheckpointRequest;
import fr.insastronaute.webapi.treasurehunt.dto.AdminPathInitializationRequest;
import fr.insastronaute.webapi.treasurehunt.dto.AdminCheckpointResponse;
import fr.insastronaute.webapi.treasurehunt.dto.AdminTreasureHuntPathResponse;
import fr.insastronaute.webapi.treasurehunt.dto.AdminTreasureHuntPathUpdateRequest;
import fr.insastronaute.webapi.treasurehunt.dto.AdminTreasureHuntContentResponse;
import fr.insastronaute.webapi.treasurehunt.dto.OrderedIdsRequest;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntContentRequest;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntPathRepository;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntCheckpointRepository;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntVaultRepository;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntSettingsRepository;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntSettings;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntActivationStatusResponse;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntContentRepository;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;

@Service
public class TreasureHuntAdminService {
    private final TreasureHuntPathRepository pathRepository;
    private final TreasureHuntCheckpointRepository checkpointRepository;
    private final TreasureHuntVaultRepository vaultRepository;
    private final TreasureHuntStorageService storageService;
    private final DepartmentRepository departmentRepository;
    private final TreasureHuntContentRepository contentRepository;
    private final ArticleRepository articleRepository;
    private final TreasureHuntSettingsRepository settingsRepository;

    public TreasureHuntAdminService(TreasureHuntPathRepository pathRepository, TreasureHuntCheckpointRepository checkpointRepository,
            TreasureHuntVaultRepository vaultRepository, TreasureHuntStorageService storageService, DepartmentRepository departmentRepository) {
        this(pathRepository, checkpointRepository, vaultRepository, storageService, departmentRepository, null, null, null);
    }

    public TreasureHuntAdminService(TreasureHuntPathRepository pathRepository, TreasureHuntCheckpointRepository checkpointRepository,
            TreasureHuntVaultRepository vaultRepository, TreasureHuntStorageService storageService, DepartmentRepository departmentRepository,
            TreasureHuntContentRepository contentRepository, ArticleRepository articleRepository) {
        this(pathRepository, checkpointRepository, vaultRepository, storageService, departmentRepository, contentRepository, articleRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public TreasureHuntAdminService(TreasureHuntPathRepository pathRepository, TreasureHuntCheckpointRepository checkpointRepository,
            TreasureHuntVaultRepository vaultRepository, TreasureHuntStorageService storageService, DepartmentRepository departmentRepository,
            TreasureHuntContentRepository contentRepository, ArticleRepository articleRepository, TreasureHuntSettingsRepository settingsRepository) {
        this.pathRepository = pathRepository;
        this.checkpointRepository = checkpointRepository;
        this.vaultRepository = vaultRepository;
        this.storageService = storageService;
        this.departmentRepository = departmentRepository;
        this.contentRepository = contentRepository;
        this.articleRepository = articleRepository;
        this.settingsRepository = settingsRepository;
    }

    @Transactional(readOnly = true)
    public TreasureHuntActivationStatusResponse activationStatus() {
        Optional<TreasureHuntSettings> settings = settingsRepository == null ? Optional.empty() : settingsRepository.findById(true);
        boolean active = settings.map(TreasureHuntSettings::isActive).orElse(false);
        boolean visible = !settings.map(TreasureHuntSettings::isHidden).orElse(false);
        return new TreasureHuntActivationStatusResponse(active, visible, active ? List.of() : activationViolations());
    }

    /**
     * Masquer n'éteint pas la chasse et l'allumer ne la révèle pas : une retouche
     * de contenu désactive la chasse au passage, et perdre le choix d'affichage
     * à chaque correction de texte serait un piège.
     */
    @Transactional
    public TreasureHuntActivationStatusResponse setVisible(boolean visible) {
        TreasureHuntSettings settings = settingsRepository.findById(true).orElseGet(TreasureHuntSettings::new);
        settings.setHidden(!visible);
        settingsRepository.save(settings);
        return activationStatus();
    }

    @Transactional
    public TreasureHuntActivationStatusResponse activate() {
        List<String> violations = activationViolations();
        if (!violations.isEmpty()) return new TreasureHuntActivationStatusResponse(false, isVisible(), violations);
        TreasureHuntSettings settings = settingsRepository.findById(true).orElseGet(TreasureHuntSettings::new);
        settings.setActive(true); settingsRepository.save(settings);
        return new TreasureHuntActivationStatusResponse(true, !settings.isHidden(), List.of());
    }

    @Transactional
    public TreasureHuntActivationStatusResponse deactivate() {
        TreasureHuntSettings settings = settingsRepository.findById(true).orElseGet(TreasureHuntSettings::new);
        settings.setActive(false); settingsRepository.save(settings);
        return new TreasureHuntActivationStatusResponse(false, !settings.isHidden(), List.of());
    }

    @Transactional
    public void deactivateIfActive() {
        settingsRepository.findById(true).filter(TreasureHuntSettings::isActive).ifPresent(settings -> {
            settings.setActive(false);
            settingsRepository.save(settings);
        });
    }

    private boolean isVisible() {
        return !settingsRepository.findById(true).map(TreasureHuntSettings::isHidden).orElse(false);
    }

    private List<String> activationViolations() {
        List<String> violations = new java.util.ArrayList<>();
        List<TreasureHuntPath> paths = pathRepository.findAllByOrderByPositionAsc();
        if (paths.size() != 6 || paths.stream().map(TreasureHuntPath::getDepartment).distinct().count() != 6) violations.add("Six voies de départements distincts sont requises.");
        for (TreasureHuntPath path : paths) {
            List<TreasureHuntCheckpoint> checkpoints = checkpointRepository.findByPathIdOrderByStepNumberAsc(path.getId());
            if (checkpoints.size() != 3) { violations.add("La voie " + path.getName() + " doit contenir exactement trois étapes."); continue; }
            if (checkpoints.stream().anyMatch(checkpoint -> checkpoint.getCode() == null || checkpoint.getCode().isBlank())) violations.add("Chaque étape de la voie " + path.getName() + " doit avoir un code.");
            // Un secret que son outil ne sait pas replier ne pourra jamais etre valide : il
            // est hache sous une forme que le joueur ne peut pas reproduire. Ce n'est pas une
            // contrainte de longueur -- une melodie d'une seule note reste parfaitement
            // valide -- mais de vocabulaire : chaque jeton doit etre une des douze notes.
            if (checkpoints.stream().filter(checkpoint -> checkpoint.getCode() != null && !checkpoint.getCode().isBlank())
                    .anyMatch(checkpoint -> TreasureHuntSecretCodec.normalize(checkpoint.getInputTool(), checkpoint.getCode()).isEmpty()))
                violations.add("Chaque étape de la voie " + path.getName() + " doit avoir un code que son outil de saisie sait lire.");
            // Une etape peut se configurer sur un outil que le front joueur ne dessine pas
            // encore : c'est ainsi qu'un telegraphe se prepare a l'avance, et c'est pourquoi
            // le choix offert a l'administrateur ne cache rien. Elle ne peut pas pour autant
            // partir en jeu -- personne ne pourrait la franchir. La garde est donc ici, a
            // l'activation, et nulle part ailleurs.
            checkpoints.stream().filter(checkpoint -> checkpoint.getInputTool() != null
                            && !checkpoint.getInputTool().isImplementedByThePlayerFront())
                    .forEach(checkpoint -> violations.add("L'étape " + checkpoint.getStepNumber() + " de la voie "
                            + path.getName() + " attend l'outil " + checkpoint.getInputTool()
                            + ", que le front joueur ne sait pas encore afficher : elle serait injouable."));
            // Une empreinte calculee sous une regle anterieure rend l'etape injouable a vie :
            // depuis que c'est la forme canonique d'une melodie qui est hachee et non sa
            // chaine brute, toute etape piano enregistree avant ce changement porte une
            // empreinte que le joueur ne peut plus reproduire. Un hash ne s'inverse pas, mais
            // `code` est stocke en clair : re-hacher son repli et comparer suffit.
            checkpoints.stream().filter(TreasureHuntAdminService::hasStaleCodeHash).forEach(checkpoint ->
                    violations.add("L'étape " + checkpoint.getStepNumber() + " de la voie " + path.getName()
                            + " porte une empreinte périmée : réenregistrez son code pour la rendre jouable."));
            long finalCodes = checkpoints.stream().flatMap(checkpoint -> contentRepository.findByCheckpointIdOrderByPositionAsc(checkpoint.getId()).stream()).filter(content -> content.getType() == TreasureHuntContentType.FINAL_CODE).count();
            boolean finalOnLast = contentRepository.findByCheckpointIdOrderByPositionAsc(checkpoints.get(2).getId()).stream().anyMatch(content -> content.getType() == TreasureHuntContentType.FINAL_CODE);
            if (finalCodes != 1 || !finalOnLast) violations.add("La voie " + path.getName() + " doit avoir un unique code final sur la troisième étape.");
        }
        if (contentRepository.countByVaultId(true) == 0) violations.add("Le coffre final doit contenir au moins un contenu.");
        return violations;
    }

    /**
     * L'empreinte stockee ne correspond plus au code replie par l'outil de l'etape.
     *
     * Rend faux dans les deux cas deja nommes par une autre violation, pour ne pas les dire
     * deux fois : une etape sans code, et une etape dont l'outil ne sait pas lire le code --
     * il n'y a alors aucun repli auquel comparer. Une etape clavier legitime concorde, la
     * normalisation d'un outil non-piano etant exactement le trim et les majuscules sous
     * lesquels son code a toujours ete hache.
     */
    private static boolean hasStaleCodeHash(TreasureHuntCheckpoint checkpoint) {
        if (checkpoint.getCode() == null || checkpoint.getCode().isBlank() || checkpoint.getCodeHash() == null) return false;
        return TreasureHuntSecretCodec.normalize(checkpoint.getInputTool(), checkpoint.getCode())
                .map(canonical -> !MessageDigest.isEqual(checkpoint.getCodeHash(), hash(canonical)))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<AdminTreasureHuntPathResponse> listPaths() {
        return pathRepository.findAllByOrderByPositionAsc().stream().map(this::toPathResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminCheckpointResponse> listCheckpoints(UUID pathId) {
        if (!pathRepository.existsById(pathId)) throw new NotFoundException("Voie introuvable.");
        return checkpointRepository.findByPathIdOrderByStepNumberAsc(pathId).stream()
                .map(this::toCheckpointResponse)
                .toList();
    }

    @Transactional
    public List<AdminTreasureHuntPathResponse> initializeFixedPaths(AdminPathInitializationRequest request) {
        if (pathRepository.count() != 0) throw new BadRequestException("Les voies sont déjà configurées.");
        if (new HashSet<>(request.departmentIds()).size() != 6) throw new BadRequestException("Les six départements doivent être distincts.");
        for (int index = 0; index < request.departmentIds().size(); index++) {
            var department = departmentRepository.findById(request.departmentIds().get(index)).orElseThrow(() -> new NotFoundException("Département introuvable."));
            TreasureHuntPath path = new TreasureHuntPath();
            path.setDepartment(department);
            path.setPosition(index + 1);
            path.setName(department.getName());
            pathRepository.save(path);
        }
        return listPaths();
    }

    @Transactional
    public AdminTreasureHuntPathResponse updatePath(Integer position, AdminTreasureHuntPathUpdateRequest request) {
        TreasureHuntPath path = pathRepository.findByPosition(position)
                .orElseThrow(() -> new NotFoundException("Voie introuvable."));
        path.setName(request.name().trim());
        return toPathResponse(path);
    }

    @Transactional
    public List<AdminTreasureHuntPathResponse> reorderPaths(OrderedIdsRequest request) {
        List<TreasureHuntPath> paths = pathRepository.findAllByOrderByPositionAsc();
        requireSameIds(request.ids(), paths.stream().map(TreasureHuntPath::getId).toList(), "Les six voies doivent être fournies.");
        paths.forEach(path -> path.setPosition(path.getPosition() + 6));
        pathRepository.flush();
        java.util.Map<UUID, TreasureHuntPath> byId = paths.stream().collect(java.util.stream.Collectors.toMap(TreasureHuntPath::getId, path -> path));
        for (int index = 0; index < request.ids().size(); index++) byId.get(request.ids().get(index)).setPosition(index + 1);
        return listPaths();
    }

    private AdminTreasureHuntPathResponse toPathResponse(TreasureHuntPath path) {
        return new AdminTreasureHuntPathResponse(path.getId(), path.getDepartment().getId(), path.getPosition(), path.getName());
    }

    @Transactional
    public AdminCheckpointResponse createCheckpoint(UUID pathId, AdminCheckpointRequest request) {
        TreasureHuntPath path = pathRepository.findById(pathId).orElseThrow(() -> new NotFoundException("Voie introuvable."));
        TreasureHuntCheckpoint checkpoint = new TreasureHuntCheckpoint();
        checkpoint.setPath(path);
        checkpoint.setStepNumber(checkpointRepository.countByPathId(pathId) + 1);
        // A la creation, un outil absent vaut KEYPAD : un panneau anterieur a l'etabli
        // n'envoie pas ce champ, et les etapes qu'il cree sont des claviers.
        apply(checkpoint, request, request.inputTool() == null ? TreasureHuntInputTool.KEYPAD : request.inputTool());
        return toCheckpointResponse(checkpointRepository.save(checkpoint));
    }

    @Transactional
    public AdminCheckpointResponse updateCheckpoint(UUID checkpointId, AdminCheckpointRequest request) {
        TreasureHuntCheckpoint checkpoint = checkpointRepository.findById(checkpointId).orElseThrow(() -> new NotFoundException("Étape introuvable."));
        // A la mise a jour, un outil absent vaut << inchange >> et non KEYPAD : un panneau
        // reste en cache qui corrige un code ne doit pas ramener une etape piano au clavier.
        apply(checkpoint, request, request.inputTool() == null ? checkpoint.getInputTool() : request.inputTool());
        return toCheckpointResponse(checkpoint);
    }

    @Transactional
    public void deleteCheckpoint(UUID checkpointId) {
        TreasureHuntCheckpoint checkpoint = checkpointRepository.findById(checkpointId).orElseThrow(() -> new NotFoundException("Étape introuvable."));
        List<TreasureHuntCheckpoint> following = checkpointRepository.findByPathIdOrderByStepNumberAsc(checkpoint.getPath().getId()).stream()
                .filter(item -> item.getStepNumber() > checkpoint.getStepNumber()).toList();
        checkpointRepository.delete(checkpoint);
        checkpointRepository.flush();
        following.forEach(item -> item.setStepNumber(item.getStepNumber() - 1));
    }

    @Transactional
    public List<AdminCheckpointResponse> reorderCheckpoints(UUID pathId, OrderedIdsRequest request) {
        if (!pathRepository.existsById(pathId)) throw new NotFoundException("Voie introuvable.");
        List<TreasureHuntCheckpoint> checkpoints = checkpointRepository.findByPathIdOrderByStepNumberAsc(pathId);
        requireSameIds(request.ids(), checkpoints.stream().map(TreasureHuntCheckpoint::getId).toList(), "Toutes les étapes de la voie doivent être fournies.");
        checkpoints.forEach(checkpoint -> checkpoint.setStepNumber(checkpoint.getStepNumber() + 100000));
        checkpointRepository.flush();
        java.util.Map<UUID, TreasureHuntCheckpoint> byId = checkpoints.stream().collect(java.util.stream.Collectors.toMap(TreasureHuntCheckpoint::getId, item -> item));
        for (int index = 0; index < request.ids().size(); index++) byId.get(request.ids().get(index)).setStepNumber(index + 1);
        return checkpointRepository.findByPathIdOrderByStepNumberAsc(pathId).stream().map(this::toCheckpointResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminTreasureHuntContentResponse> listCheckpointContents(UUID checkpointId) {
        checkpoint(checkpointId);
        return contentRepository.findByCheckpointIdOrderByPositionAsc(checkpointId).stream().map(this::toContentResponse).toList();
    }

    @Transactional
    public AdminTreasureHuntContentResponse createCheckpointContent(UUID checkpointId, TreasureHuntContentRequest request) {
        TreasureHuntCheckpoint checkpoint = checkpoint(checkpointId);
        TreasureHuntContent content = new TreasureHuntContent();
        content.setCheckpoint(checkpoint);
        content.setPosition((int) contentRepository.countByCheckpointId(checkpointId) + 1);
        apply(content, request, checkpoint);
        return toContentResponse(contentRepository.save(content));
    }

    @Transactional
    public AdminTreasureHuntContentResponse uploadCheckpointContent(UUID checkpointId, MultipartFile file) {
        TreasureHuntCheckpoint checkpoint = checkpoint(checkpointId);
        TreasureHuntContent content = new TreasureHuntContent();
        content.setCheckpoint(checkpoint); content.setPosition((int) contentRepository.countByCheckpointId(checkpointId) + 1);
        content.setType(TreasureHuntContentType.MEDIA); content.setStoragePath(storageService.store(file));
        return toContentResponse(contentRepository.save(content));
    }

    @Transactional(readOnly = true)
    public List<AdminTreasureHuntContentResponse> listVaultContents() {
        return contentRepository.findByVaultIdOrderByPositionAsc(true).stream().map(this::toContentResponse).toList();
    }

    @Transactional
    public AdminTreasureHuntContentResponse createVaultContent(TreasureHuntContentRequest request) {
        TreasureHuntVault vault = vaultRepository.findById(true).orElseGet(() -> vaultRepository.save(new TreasureHuntVault()));
        TreasureHuntContent content = new TreasureHuntContent(); content.setVault(vault);
        content.setPosition((int) contentRepository.countByVaultId(true) + 1); apply(content, request, null);
        return toContentResponse(contentRepository.save(content));
    }

    @Transactional
    public AdminTreasureHuntContentResponse uploadVaultContent(MultipartFile file) {
        TreasureHuntVault vault = vaultRepository.findById(true).orElseGet(() -> vaultRepository.save(new TreasureHuntVault()));
        TreasureHuntContent content = new TreasureHuntContent(); content.setVault(vault);
        content.setPosition((int) contentRepository.countByVaultId(true) + 1); content.setType(TreasureHuntContentType.MEDIA); content.setStoragePath(storageService.store(file));
        return toContentResponse(contentRepository.save(content));
    }

    @Transactional
    public AdminTreasureHuntContentResponse updateContent(UUID contentId, TreasureHuntContentRequest request) {
        TreasureHuntContent content = contentRepository.findById(contentId).orElseThrow(() -> new NotFoundException("Contenu introuvable."));
        apply(content, request, content.getCheckpoint()); return toContentResponse(content);
    }

    @Transactional
    public void deleteContent(UUID contentId) {
        TreasureHuntContent content = contentRepository.findById(contentId).orElseThrow(() -> new NotFoundException("Contenu introuvable."));
        List<TreasureHuntContent> following = contentsFor(content).stream().filter(item -> item.getPosition() > content.getPosition()).toList();
        contentRepository.delete(content); contentRepository.flush(); following.forEach(item -> item.setPosition(item.getPosition() - 1));
    }

    @Transactional
    public List<AdminTreasureHuntContentResponse> reorderCheckpointContents(UUID checkpointId, OrderedIdsRequest request) {
        checkpoint(checkpointId); return reorderContents(contentRepository.findByCheckpointIdOrderByPositionAsc(checkpointId), request);
    }

    @Transactional
    public List<AdminTreasureHuntContentResponse> reorderVaultContents(OrderedIdsRequest request) {
        return reorderContents(contentRepository.findByVaultIdOrderByPositionAsc(true), request);
    }

    /**
     * L'outil arrive deja resolu : c'est l'appelant qui sait ce que son absence veut dire,
     * et la normalisation du secret depend de lui -- il doit donc etre connu avant.
     *
     * Le hash est calcule sur la forme canonique du secret, la meme que le chemin de
     * validation du joueur reconstruira. Sans ce repliage, une melodie ecrite "DO RE MI"
     * etait hachee telle quelle alors que le joueur enverrait "DO-RE-MI" : toute etape
     * piano ecrite autrement que sous la forme canonique exacte etait injouable a vie.
     * Le champ code, lui, garde la forme lisible que l'administrateur a tapee, pour
     * l'affichage du panneau.
     *
     * Un secret que son outil ne sait pas replier est refuse par un message explicite :
     * nous sommes cote administration, ou un message clair ne fuite rien -- contrairement
     * au cote joueur, ou il designerait l'outil attendu.
     */
    private void apply(TreasureHuntCheckpoint checkpoint, AdminCheckpointRequest request, TreasureHuntInputTool tool) {
        String code = request.code().trim();
        byte[] codeHash = hash(TreasureHuntSecretCodec.normalize(tool, code)
                .orElseThrow(() -> new BadRequestException(unreadableSecretMessage(tool))));
        boolean codeAlreadyUsed = checkpointRepository.findByCodeHash(codeHash)
                .filter(existing -> !existing.getId().equals(checkpoint.getId()))
                .isPresent();
        // L'unicite porte sur le hash, donc sur la melodie et non sur son ecriture :
        // "DO RE MI" et "DO-RE-MI" sont le meme secret, et le second entre en conflit.
        if (codeAlreadyUsed) throw new ConflictException("Ce code est déjà utilisé.");
        // Valider d'abord, muter ensuite : sur une mise a jour l'entite est geree par JPA, et
        // poser l'outil avant le repli faisait passer une etape clavier en piano puis levait.
        // Le rollback transactionnel rattrapait cet etat intermediaire ; ne jamais l'ecrire
        // vaut mieux que dependre de lui.
        checkpoint.setInputTool(tool);
        checkpoint.setCode(code);
        checkpoint.setCodeHash(codeHash);
    }

    private static String unreadableSecretMessage(TreasureHuntInputTool tool) {
        return tool == TreasureHuntInputTool.PIANO
                ? "La mélodie doit être une suite de notes parmi DO, DO♯, RÉ, RÉ♯, MI, FA, FA♯, SOL, SOL♯, LA, LA♯, SI."
                : "Ce code ne peut pas être vide.";
    }
    private void apply(TreasureHuntContent content, TreasureHuntContentRequest request, TreasureHuntCheckpoint checkpoint) {
        if (request.type() == TreasureHuntContentType.MEDIA) throw new BadRequestException("Un média doit être téléversé.");
        if (request.type() == TreasureHuntContentType.ARTICLE && (request.articleId() == null || articleRepository == null || !articleRepository.existsById(request.articleId()))) throw new NotFoundException("Article introuvable.");
        if (request.type() == TreasureHuntContentType.FINAL_CODE) {
            if (checkpoint == null) throw new BadRequestException("Le code final ne peut pas être placé dans le coffre.");
            if (checkpoint.getStepNumber() != checkpointRepository.countByPathId(checkpoint.getPath().getId())) throw new BadRequestException("Le code final doit être sur la dernière étape.");
        }
        content.setType(request.type()); content.setText(request.text()); content.setArticleId(request.articleId());
        content.setStoragePath(null);
    }
    private TreasureHuntCheckpoint checkpoint(UUID checkpointId) { return checkpointRepository.findById(checkpointId).orElseThrow(() -> new NotFoundException("Étape introuvable.")); }
    private List<TreasureHuntContent> contentsFor(TreasureHuntContent content) { return content.getCheckpoint() != null ? contentRepository.findByCheckpointIdOrderByPositionAsc(content.getCheckpoint().getId()) : contentRepository.findByVaultIdOrderByPositionAsc(true); }
    private List<AdminTreasureHuntContentResponse> reorderContents(List<TreasureHuntContent> contents, OrderedIdsRequest request) {
        requireSameIds(request.ids(), contents.stream().map(TreasureHuntContent::getId).toList(), "Tous les contenus doivent être fournis.");
        contents.forEach(content -> content.setPosition(content.getPosition() + 100000)); contentRepository.flush();
        java.util.Map<UUID, TreasureHuntContent> byId = contents.stream().collect(java.util.stream.Collectors.toMap(TreasureHuntContent::getId, item -> item));
        for (int index = 0; index < request.ids().size(); index++) byId.get(request.ids().get(index)).setPosition(index + 1);
        return request.ids().stream().map(byId::get).map(this::toContentResponse).toList();
    }
    private void requireSameIds(List<UUID> requested, List<UUID> actual, String message) { if (new HashSet<>(requested).size() != requested.size() || requested.size() != actual.size() || !new HashSet<>(requested).containsAll(actual)) throw new BadRequestException(message); }
    private AdminTreasureHuntContentResponse toContentResponse(TreasureHuntContent content) { return new AdminTreasureHuntContentResponse(content.getId(), content.getPosition(), content.getType(), content.getText(), content.getArticleId(), content.getStoragePath() != null); }
    private AdminCheckpointResponse toCheckpointResponse(TreasureHuntCheckpoint checkpoint) {
        return new AdminCheckpointResponse(checkpoint.getId(), checkpoint.getPath().getId(), checkpoint.getStepNumber(), checkpoint.getCode(),
                checkpoint.getInputTool());
    }
    private static byte[] hash(String code) { try { return MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)); } catch (Exception exception) { throw new IllegalStateException(exception); } }
}
