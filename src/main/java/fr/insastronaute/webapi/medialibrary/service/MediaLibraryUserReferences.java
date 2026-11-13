package fr.insastronaute.webapi.medialibrary.service;

import fr.insastronaute.webapi.medialibrary.repository.MediaLibraryExportItemRepository;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibraryExportRepository;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibraryGrantRepository;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibrarySelectionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Ce que la Mediatheque retient d'un compte, et qui l'empeche de disparaitre.
 *
 * <p>Quatre colonnes de V187 designent l'auteur d'un geste, sans ON DELETE : qui a
 * accorde un acces, qui a retenu un media, qui a fige un export, et qui avait retenu
 * chaque media d'un export. La suppression d'un joueur pose la question avant d'effacer
 * sa ligne : une violation de cle etrangere empoisonnerait la transaction, et le repli
 * (le marquer supprime) ne pourrait plus s'ecrire. Sa propre ligne d'acces, elle, part
 * avec lui.</p>
 */
@Component
public class MediaLibraryUserReferences {

    private final MediaLibraryGrantRepository grants;
    private final MediaLibrarySelectionRepository selections;
    private final MediaLibraryExportRepository exports;
    private final MediaLibraryExportItemRepository exportItems;

    public MediaLibraryUserReferences(MediaLibraryGrantRepository grants, MediaLibrarySelectionRepository selections,
                                      MediaLibraryExportRepository exports, MediaLibraryExportItemRepository exportItems) {
        this.grants = grants;
        this.selections = selections;
        this.exports = exports;
        this.exportItems = exportItems;
    }

    @Transactional(readOnly = true)
    public boolean isReferenced(UUID userId) {
        return grants.existsByGrantedBy_Id(userId)
                || selections.existsBySelectedBy_Id(userId)
                || exports.existsByCreatedBy_Id(userId)
                || exportItems.existsBySelectedBy_Id(userId);
    }
}
