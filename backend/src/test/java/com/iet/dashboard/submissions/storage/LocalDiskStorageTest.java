package com.iet.dashboard.submissions.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalDiskStorageTest {

    @TempDir
    Path tempDir;

    private LocalDiskStorage storage;

    @BeforeEach
    void setUp() throws IOException {
        storage = new LocalDiskStorage(tempDir.toString());
        storage.init();
    }

    @Test
    void testStoreAndLoadResource_Success() throws IOException {
        String content = "Hello IET Faculty Dashboard";
        ByteArrayInputStream input = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        String key = storage.store(input, "syllabus.pdf");
        assertNotNull(key);
        assertTrue(key.contains("syllabus.pdf"));

        Resource resource = storage.loadAsResource(key);
        assertTrue(resource.exists());
        assertTrue(resource.isReadable());
        assertEquals(content, new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void testLoadNonExistentFile_ThrowsFileNotFoundException() {
        assertThrows(FileNotFoundException.class, () -> storage.loadAsResource("non-existent-key.pdf"));
    }

    @Test
    void testDelete_Success() throws IOException {
        String content = "Temporary Content";
        String key = storage.store(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), "temp.txt");

        Resource resource = storage.loadAsResource(key);
        assertTrue(resource.exists());

        storage.delete(key);

        assertThrows(FileNotFoundException.class, () -> storage.loadAsResource(key));
    }
}
