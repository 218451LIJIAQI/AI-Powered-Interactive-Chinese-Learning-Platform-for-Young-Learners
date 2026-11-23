package com.example.aichinese.repository.projection;

import com.example.aichinese.entity.enums.ContentStatus;

public interface StatusCountProjection {

    ContentStatus getStatus();

    long getCount();
}
