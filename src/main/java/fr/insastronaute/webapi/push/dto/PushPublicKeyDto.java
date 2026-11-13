package fr.insastronaute.webapi.push.dto;

/**
 * La cle que le navigateur passe a {@code applicationServerKey}.
 *
 * <p>{@code enabled} dit au front s'il doit meme proposer la chose : push eteint, il ne
 * demande pas une permission qu'un refus rendrait definitif.</p>
 */
public record PushPublicKeyDto(boolean enabled, String publicKey) {
}
