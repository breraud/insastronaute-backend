package fr.insastronaute.webapi.sts.multiplayer.combat;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.sts.combat.command.RejectionCode;
import java.util.Objects;

/**
 * A command the engine refused, carrying the engine's own reason.
 *
 * <p>PvP used to throw a bare {@code BadRequestException} here, and the socket layer -- which
 * reads the failure's type to decide what to tell the client -- had nothing left to read. Every
 * engine refusal reached the player as {@code INVALID_COMMAND}: not enough energy, a card no
 * longer in hand and an impossible target were one and the same message, on a channel where the
 * player cannot see the server's logs to find out which.
 *
 * <p>PvE has said all along which of them it was. This is how PvP says it too.
 */
public class StsPvpCommandRejectedException extends BadRequestException {

    private final transient RejectionCode code;

    public StsPvpCommandRejectedException(RejectionCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public RejectionCode code() {
        return code;
    }
}
