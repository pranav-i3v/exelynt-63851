package com.exelynt.booking.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exelynt.booking.audit.common.AuditAction;
import com.exelynt.booking.audit.service.AuditService;
import com.exelynt.booking.common.exception.ConflictException;
import com.exelynt.booking.common.exception.NotFoundException;
import com.exelynt.booking.resource.common.ResourceType;
import com.exelynt.booking.resource.dto.ResourceRequest;
import com.exelynt.booking.resource.dto.ResourceResponse;
import java.util.Optional;

import com.exelynt.booking.resource.entity.Resource;
import com.exelynt.booking.resource.repository.ResourceRepository;
import com.exelynt.booking.resource.service.ResourceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

    @Mock
    private ResourceRepository resourceRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private ResourceService resourceService;

    @Test
    @DisplayName("creating a resource stores it and writes an audit row")
    void createsResource() {
        ResourceRequest request = new ResourceRequest("Room A", ResourceType.ROOM, "desc", null);
        when(resourceRepository.existsByNameIgnoreCase("Room A")).thenReturn(false);
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> {
            Resource resource = invocation.getArgument(0);
            ReflectionTestUtils.setField(resource, "id", 11L);
            return resource;
        });

        ResourceResponse response = resourceService.create(request);

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.active()).isTrue();
        assertThat(response.type()).isEqualTo(ResourceType.ROOM);
        verify(auditService).record(null, AuditAction.RESOURCE_CREATED, "Resource", 11L);
    }

    @Test
    @DisplayName("a duplicate name is a 409")
    void rejectsDuplicateName() {
        when(resourceRepository.existsByNameIgnoreCase("Room A")).thenReturn(true);

        assertThatThrownBy(() -> resourceService.create(new ResourceRequest("Room A", ResourceType.ROOM, null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");

        verify(resourceRepository, never()).save(any());
    }

    @Test
    @DisplayName("an unknown id is a 404")
    void rejectsUnknownId() {
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceService.findById(99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("updating replaces every field and writes an audit row")
    void updatesResource() {
        Resource existing = new Resource("Old", ResourceType.ROOM, "old", true);
        ReflectionTestUtils.setField(existing, "id", 5L);
        when(resourceRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(resourceRepository.existsByNameIgnoreCaseAndIdNot("New", 5L)).thenReturn(false);
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResourceResponse response = resourceService.update(
                5L, new ResourceRequest("New", ResourceType.EQUIPMENT, "new", false));

        assertThat(response.name()).isEqualTo("New");
        assertThat(response.type()).isEqualTo(ResourceType.EQUIPMENT);
        assertThat(response.active()).isFalse();
        verify(auditService).record(null, AuditAction.RESOURCE_UPDATED, "Resource", 5L);
    }

    @Test
    @DisplayName("deleting removes the row and writes an audit row")
    void deletesResource() {
        Resource existing = new Resource("Old", ResourceType.ROOM, "old", true);
        ReflectionTestUtils.setField(existing, "id", 5L);
        when(resourceRepository.findById(5L)).thenReturn(Optional.of(existing));

        resourceService.delete(5L);

        verify(resourceRepository).delete(existing);
        verify(auditService).record(null, AuditAction.RESOURCE_DELETED, "Resource", 5L);
    }
}
