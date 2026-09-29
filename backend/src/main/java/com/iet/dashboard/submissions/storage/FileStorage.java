package com.iet.dashboard.submissions.storage;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

public interface FileStorage {

    String store(InputStream inputStream, String originalFilename) throws IOException;

    Resource loadAsResource(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;
}
