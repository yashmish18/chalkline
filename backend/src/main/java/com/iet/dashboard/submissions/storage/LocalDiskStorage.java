package com.iet.dashboard.submissions.storage;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
public class LocalDiskStorage implements FileStorage {

    private final Path rootLocation;

    public LocalDiskStorage(@Value("${app.storage.upload-dir:./data/uploads}") String uploadDir) {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(this.rootLocation);
    }

    @Override
    public String store(InputStream inputStream, String originalFilename) throws IOException {
        String safeName = (originalFilename != null)
                ? Paths.get(originalFilename).getFileName().toString().replaceAll("[^a-zA-Z0-9._-]", "_")
                : "file";
        String storageKey = UUID.randomUUID() + "_" + safeName;
        Path destinationFile = this.rootLocation.resolve(storageKey).normalize();

        if (!destinationFile.getParent().equals(this.rootLocation)) {
            throw new SecurityException("Cannot store file outside specified directory");
        }

        Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
        return storageKey;
    }

    @Override
    public Resource loadAsResource(String storageKey) throws IOException {
        Path file = this.rootLocation.resolve(storageKey).normalize();
        if (!file.getParent().equals(this.rootLocation)) {
            throw new SecurityException("Cannot access file outside specified directory");
        }
        Resource resource = new UrlResource(file.toUri());
        if (resource.exists() && resource.isReadable()) {
            return resource;
        } else {
            throw new FileNotFoundException("Could not read file with key: " + storageKey);
        }
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Path file = this.rootLocation.resolve(storageKey).normalize();
        if (!file.getParent().equals(this.rootLocation)) {
            throw new SecurityException("Cannot delete file outside specified directory");
        }
        Files.deleteIfExists(file);
    }
}
