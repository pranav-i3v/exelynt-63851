package com.exelynt.booking.resource.dto;

import com.exelynt.booking.resource.entity.Resource;

/** Entity to DTO translation; entities never cross the API boundary. */
public final class ResourceMapper {

    private ResourceMapper() {
    }

    public static ResourceResponse toResponse(Resource resource) {
        return new ResourceResponse(
                resource.getId(),
                resource.getName(),
                resource.getType(),
                resource.getDescription(),
                resource.isActive(),
                resource.getCreatedAt(),
                resource.getUpdatedAt(),
                resource.getCreatedBy(),
                resource.getUpdatedBy());
    }
}
