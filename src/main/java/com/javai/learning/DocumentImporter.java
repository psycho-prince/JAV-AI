package com.javai.learning;

import java.io.File;
import java.nio.file.Files;

public class DocumentImporter {

    public String importDocument(File file) throws Exception {
        if (!file.exists()) {
            throw new IllegalArgumentException("Document file not found: " + file.getAbsolutePath());
        }
        return Files.readString(file.toPath());
    }
}
