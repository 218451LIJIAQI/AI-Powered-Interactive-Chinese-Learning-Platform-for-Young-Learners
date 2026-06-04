package com.example.aichinese.repository.projection;

import org.springframework.lang.Nullable;

public interface StatusCountProjection {

    @Nullable
    String getStatus();

    long getCount();
}
