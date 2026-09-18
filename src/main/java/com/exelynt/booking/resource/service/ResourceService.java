package com.exelynt.booking.resource.service;

import com.exelynt.booking.audit.common.AuditAction;
import com.exelynt.booking.audit.service.AuditService;
import com.exelynt.booking.common.exception.type.ConflictException;
import com.exelynt.booking.common.exception.type.NotFoundException;
import com.exelynt.booking.resource.dto.ResourceMapper;
import com.exelynt.booking.resource.dto.ResourceRequest;
import com.exelynt.booking.resource.dto.ResourceResponse;
import com.exelynt.booking.resource.entity.Resource;
import com.exelynt.booking.resource.repository.ResourceRepository;
import com.exelynt.booking.security.SecurityUtils;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceService {

    private static final String ENTITY_TYPE = "Resource";

    private final ResourceRepository resourceRepository;
    private final AuditService auditService;

    public ResourceService(ResourceRepository resourceRepository, AuditService auditService) {
        this.resourceRepository = resourceRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ResourceResponse> findAll() {
        return resourceRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(ResourceMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResourceResponse findById(Long id) {
        return ResourceMapper.toResponse(getOrThrow(id));
    }

    @Transactional
    public ResourceResponse create(ResourceRequest request) {
        if (resourceRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("A resource named '" + request.name() + "' already exists");
        }
        Resource saved = resourceRepository.save(new Resource(
                request.name(), request.type(), request.description(), request.activeOrDefault()));
        audit(AuditAction.RESOURCE_CREATED, saved.getId());
        return ResourceMapper.toResponse(saved);
    }

    @Transactional
    public ResourceResponse update(Long id, ResourceRequest request) {
        Resource resource = getOrThrow(id);
        if (resourceRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new ConflictException("A resource named '" + request.name() + "' already exists");
        }
        resource.setName(request.name());
        resource.setType(request.type());
        resource.setDescription(request.description());
        resource.setActive(request.activeOrDefault());
        Resource saved = resourceRepository.save(resource);
        audit(AuditAction.RESOURCE_UPDATED, id);
        return ResourceMapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Resource resource = getOrThrow(id);
        resourceRepository.delete(resource);
        audit(AuditAction.RESOURCE_DELETED, id);
    }

    @Transactional(readOnly = true)
    public Resource getEntityOrThrow(Long id) {
        return getOrThrow(id);
    }

    private Resource getOrThrow(Long id) {
        return resourceRepository.findById(id).orElseThrow(() -> NotFoundException.of(ENTITY_TYPE, id));
    }

    private void audit(AuditAction action, Long id) {
        auditService.record(SecurityUtils.currentUsername().orElse(null), action, ENTITY_TYPE, id);
    }
}
