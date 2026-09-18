package com.exelynt.booking.resource.dto;

import com.exelynt.booking.resource.ResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Create/update payload for a resource. */
public record ResourceRequest(
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @NotNull(message = "type is required and must be one of ROOM, VEHICLE, EQUIPMENT")
        ResourceType type,

        @Size(max = 500, message = "description must be at most 500 characters")
        String description,

        Boolean active) {

    /** Resources are active unless the caller says otherwise. */
    public boolean activeOrDefault() {
        return active == null || active;
    }
}
