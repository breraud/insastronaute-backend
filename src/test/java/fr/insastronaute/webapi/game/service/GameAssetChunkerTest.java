package fr.insastronaute.webapi.game.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class GameAssetChunkerTest {

    private final GameAssetChunker chunker = new GameAssetChunker();

    @Test
    void keepsBoundarySizedFilesWhole() {
        byte[] bytes = new byte[GameAssetChunker.CHUNK_SIZE_BYTES];

        assertThat(chunker.shouldChunk(bytes.length)).isFalse();
    }

    @Test
    void chunksAndHashesBytesWithoutChangingThem() throws Exception {
        byte[] bytes = new byte[GameAssetChunker.CHUNK_SIZE_BYTES + 1];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) (index % 251);
        }

        GameAssetChunker.ChunkedAsset result = chunker.chunk("Build/game.data.br", bytes);

        assertThat(result.chunks()).hasSize(2);
        assertThat(result.chunks().get(0).bytes()).hasSize(GameAssetChunker.CHUNK_SIZE_BYTES);
        assertThat(result.chunks().get(1).bytes()).hasSize(1);
        assertThat(result.sha256()).hasSize(64);

        ByteArrayOutputStream rebuilt = new ByteArrayOutputStream();
        for (GameAssetChunker.Chunk chunk : result.chunks()) {
            rebuilt.write(chunk.bytes());
            assertThat(chunk.sha256()).hasSize(64);
        }
        assertThat(rebuilt.toByteArray()).isEqualTo(bytes);
    }
}
