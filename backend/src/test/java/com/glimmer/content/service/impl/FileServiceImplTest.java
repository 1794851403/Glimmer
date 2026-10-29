package com.glimmer.content.service.impl;

import com.glimmer.common.exception.BizException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.storage.StorageService;
import com.glimmer.common.web.LoginUser;
import com.glimmer.common.web.UserContext;
import com.glimmer.content.dto.ImageUploadResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FileServiceImplTest {

    private StorageService storageService;
    private FileServiceImpl fileService;

    @BeforeEach
    void setUp() {
        storageService = mock(StorageService.class);
        fileService = new FileServiceImpl(storageService);
        UserContext.set(new LoginUser(1L, "USER"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldUploadImageWithGeneratedObjectName() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.PNG", "image/png", new byte[]{1, 2, 3});
        when(storageService.upload(any(), anyString(), eq("image/png")))
                .thenReturn("https://cdn.example.com/images/1/x.png");

        ImageUploadResponse response = fileService.uploadImage(file);

        assertThat(response.url()).isEqualTo("https://cdn.example.com/images/1/x.png");
        assertThat(response.size()).isEqualTo(3);
        assertThat(response.contentType()).isEqualTo("image/png");

        ArgumentCaptor<String> objectName = ArgumentCaptor.forClass(String.class);
        verify(storageService).upload(any(), objectName.capture(), eq("image/png"));
        assertThat(objectName.getValue())
                .matches("images/1/\\d{4}/\\d{2}/\\d{2}/[0-9a-f]{32}\\.png");
    }

    @Test
    void shouldRejectEmptyFile() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> fileService.uploadImage(file))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.UPLOAD_TYPE_NOT_ALLOWED));
        verifyNoInteractions(storageService);
    }

    @Test
    void shouldRejectTooLargeFile() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(5L * 1024 * 1024 + 1);

        assertThatThrownBy(() -> fileService.uploadImage(file))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.UPLOAD_TOO_LARGE));
        verifyNoInteractions(storageService);
    }

    @Test
    void shouldRejectUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "anim.gif", "image/gif", new byte[]{1});

        assertThatThrownBy(() -> fileService.uploadImage(file))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.UPLOAD_TYPE_NOT_ALLOWED));
        verifyNoInteractions(storageService);
    }

    @Test
    void shouldRejectFilenameWithoutExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo", "image/png", new byte[]{1});

        assertThatThrownBy(() -> fileService.uploadImage(file))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.UPLOAD_TYPE_NOT_ALLOWED));
        verifyNoInteractions(storageService);
    }
}
