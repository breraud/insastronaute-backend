package fr.insastronaute.webapi.medialibrary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Une ligne d'export : son export, et sa place dans l'ordre fige. */
@Embeddable
public class MediaExportItemKey implements Serializable {

    @Column(name = "export_id", nullable = false)
    private UUID exportId;

    @Column(name = "item_position", nullable = false)
    private int itemPosition;

    protected MediaExportItemKey() {
    }

    public MediaExportItemKey(UUID exportId, int itemPosition) {
        this.exportId = exportId;
        this.itemPosition = itemPosition;
    }

    public UUID getExportId() { return exportId; }
    public int getItemPosition() { return itemPosition; }

    @Override
    public boolean equals(Object other) {
        return other instanceof MediaExportItemKey key
                && itemPosition == key.itemPosition
                && Objects.equals(exportId, key.exportId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exportId, itemPosition);
    }
}
