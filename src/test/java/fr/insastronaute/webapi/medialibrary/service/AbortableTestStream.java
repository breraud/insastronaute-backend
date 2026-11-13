package fr.insastronaute.webapi.medialibrary.service;

import software.amazon.awssdk.http.Abortable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Un flux de stockage qu'on peut abandonner, comme le ResponseInputStream du SDK S3.
 *
 * <p>Il note dans l'ordre ce qu'on lui fait : fermer un flux S3 entame sans l'abandonner
 * d'abord lit tout le reste de l'objet, pour rien.</p>
 */
public final class AbortableTestStream extends InputStream implements Abortable {

    private final ByteArrayInputStream data;
    private final boolean failsAtTheEnd;
    private final List<String> events = new ArrayList<>();

    private AbortableTestStream(byte[] bytes, boolean failsAtTheEnd) {
        this.data = new ByteArrayInputStream(bytes);
        this.failsAtTheEnd = failsAtTheEnd;
    }

    public static AbortableTestStream of(byte[] bytes) {
        return new AbortableTestStream(bytes, false);
    }

    /** Ces octets, puis une panne : S3 qui coupe au milieu de l'objet. */
    public static AbortableTestStream failingAfter(byte[] bytes) {
        return new AbortableTestStream(bytes, true);
    }

    public List<String> events() {
        return events;
    }

    @Override
    public int read() throws IOException {
        int value = data.read();
        if (value < 0 && failsAtTheEnd) {
            throw new IOException("Panne S3 simulee au milieu de l'objet");
        }
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int read = data.read(buffer, offset, length);
        if (read < 0 && failsAtTheEnd) {
            throw new IOException("Panne S3 simulee au milieu de l'objet");
        }
        return read;
    }

    @Override
    public void abort() {
        events.add("abort");
    }

    @Override
    public void close() {
        events.add("close");
    }
}
