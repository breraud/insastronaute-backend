package fr.insastronaute.webapi.place.dto;

/**
 * Un changement du canevas, poussé en direct.
 *
 * @param departmentColor la couleur du département de l'auteur du pixel. C'est ce qui
 *                        permet à la fresque des départements, simple lecture de la
 *                        fresque libre, de suivre le même topic. {@code null} pour une
 *                        case que rien ne rattache à un département : auteur sans
 *                        département, censure.
 */
public record PlaceCanvasEvent(PlaceEventType type, Integer x, Integer y, String color, String revision,
                               String departmentColor) {

    public PlaceCanvasEvent(PlaceEventType type, Integer x, Integer y, String color, String revision) {
        this(type, x, y, color, revision, null);
    }

    public PlaceCanvasEvent(PlaceEventType type, Integer x, Integer y, String color, long revision) {
        this(type, x, y, color, Long.toString(revision), null);
    }

    public static PlaceCanvasEvent pixel(int x, int y, String color, long revision) {
        return pixel(x, y, color, revision, null);
    }

    public static PlaceCanvasEvent pixel(int x, int y, String color, long revision, String departmentColor) {
        return new PlaceCanvasEvent(PlaceEventType.PIXEL, x, y, color, Long.toString(revision), departmentColor);
    }
}
