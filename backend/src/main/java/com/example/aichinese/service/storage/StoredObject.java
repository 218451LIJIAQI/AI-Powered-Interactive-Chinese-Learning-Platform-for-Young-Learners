package com.example.aichinese.service.storage;

import java.nio.file.Path;
import java.util.Objects;

public record StoredObject(Path location) {

    public StoredObject {
        location = Objects.requireNonNull(location, "location");
    }
}
