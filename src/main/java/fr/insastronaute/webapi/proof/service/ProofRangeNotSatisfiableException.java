package fr.insastronaute.webapi.proof.service;

public class ProofRangeNotSatisfiableException extends RuntimeException {
    private final long objectSize;
    public ProofRangeNotSatisfiableException(long objectSize) {
        super("Plage de média invalide.");
        this.objectSize = objectSize;
    }
    public long objectSize() { return objectSize; }
}
