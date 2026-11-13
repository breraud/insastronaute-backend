package fr.insastronaute.webapi.place.service;

public record PlaceCanvasSnapshot(byte[] png, PlaceCanvasDescriptor descriptor) {

    public PlaceCanvasSnapshot {
        png = png.clone();
    }

    @Override
    public byte[] png() {
        return png.clone();
    }
}
