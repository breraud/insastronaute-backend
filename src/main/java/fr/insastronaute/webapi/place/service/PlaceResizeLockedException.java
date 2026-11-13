package fr.insastronaute.webapi.place.service;

public class PlaceResizeLockedException extends fr.insastronaute.webapi.common.web.CodedBadRequestException {
    public PlaceResizeLockedException() {
        super("PLACE_RESIZE_IN_PROGRESS",
                "Le canevas est en cours de redimensionnement. Réessayez dans un instant.");
    }
}
