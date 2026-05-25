package com.example.aichinese.service.storage;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Result returned after a provider stores an uploaded object.
 */
public record StoredObject(Path location) {

    public StoredObject {
        location = Objects.requireNonNull(location, "location");
    }
}
