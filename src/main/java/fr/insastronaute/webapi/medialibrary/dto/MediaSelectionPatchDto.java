package fr.insastronaute.webapi.medialibrary.dto;

/**
 * Les retraits s'appliquent avant les ajouts : un meme media present des deux cotes
 * finit retenu.
 */
public record MediaSelectionPatchDto(
        @jakarta.validation.Valid @jakarta.validation.constraints.Size(max = 500) java.util.List<MediaRefDto> add,
        @jakarta.validation.Valid @jakarta.validation.constraints.Size(max = 500) java.util.List<MediaRefDto> remove
) { }
