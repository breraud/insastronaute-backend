package fr.insastronaute.webapi.common.web;

public class TooManyRequestsException extends RuntimeException {

    private final long remainingCooldownMs;

    public TooManyRequestsException(long remainingCooldownMs) {
        super("Vous devez attendre avant de pouvoir poser un nouveau pixel.");
        this.remainingCooldownMs = remainingCooldownMs;
    }

    public long getRemainingCooldownMs() {
        return remainingCooldownMs;
    }
}
