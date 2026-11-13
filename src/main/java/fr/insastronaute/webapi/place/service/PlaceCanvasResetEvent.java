package fr.insastronaute.webapi.place.service;

public record PlaceCanvasResetEvent(String type, String revision) {
    public PlaceCanvasResetEvent(String type, long revision) {
        this(type, Long.toString(revision));
    }
}
