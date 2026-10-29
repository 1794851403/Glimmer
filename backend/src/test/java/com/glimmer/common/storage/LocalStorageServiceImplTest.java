package com.glimmer.common.storage;

import com.glimmer.common.exception.BizException;
import com.glimmer.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalStorageServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldWriteFileAndReturnPublicUrl() throws Exception {
        LocalStorageServiceImpl storage = new LocalStorageServiceImpl(
                new StorageProperties("local",
                        new StorageProperties.Local(tempDir.toString(), "http://localhost:8080/"),
                        null));

        String url = storage.upload(new byte[]{1, 2, 3}, "images/1/photo.png", "image/png");

        assertThat(url).isEqualTo("http://localhost:8080/uploads/images/1/photo.png");
        assertThat(Files.readAllBytes(tempDir.resolve("images/1/photo.png"))).containsExactly(1, 2, 3);
    }

    @Test
    void shouldRejectPathTraversal() {
        LocalStorageServiceImpl storage = new LocalStorageServiceImpl(
                new StorageProperties("local",
                        new StorageProperties.Local(tempDir.toString(), "http://localhost:8080"),
                        null));

        assertThatThrownBy(() -> storage.upload(new byte[]{1}, "../evil.png", "image/png"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_PARAM));
    }
}
