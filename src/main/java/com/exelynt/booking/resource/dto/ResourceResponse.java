package com.exelynt.booking.resource.dto;

import com.exelynt.booking.resource.ResourceType;
import java.time.LocalDateTime;

public record ResourceResponse(
        Long id,
        String name,
        ResourceType type,
        String description,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String createdBy,
        String updatedBy) {
}
