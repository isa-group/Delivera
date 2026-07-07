package com.delivera.controller;

import com.delivera.worker.controller.WorkerController;
import com.delivera.worker.dto.ChangeRoleRequest;
import com.delivera.worker.dto.WorkerInviteRequest;
import com.delivera.worker.dto.WorkerResponse;
import com.delivera.worker.model.WorkerRole;
import com.delivera.worker.service.WorkerService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkerControllerTest {

    @Mock private WorkerService workerService;
    @InjectMocks private WorkerController controller;

    private static WorkerResponse sampleWorker() {
        return new WorkerResponse(UUID.randomUUID(), "w@e.com", "First", "Last", "OPERATOR", null, null);
    }

    @Test
    void list_returns200() {
        when(workerService.getByCompany()).thenReturn(List.of(sampleWorker()));
        var resp = controller.list();
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
    }

    @Test
    void invite_returns201() {
        WorkerInviteRequest req = new WorkerInviteRequest("w@e.com", WorkerRole.OPERATOR);
        WorkerResponse expected = sampleWorker();
        when(workerService.invite(req)).thenReturn(expected);
        var resp = controller.invite(req);
        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getBody()).isSameAs(expected);
    }

    @Test
    void changeRole_returns200() {
        UUID id = UUID.randomUUID();
        ChangeRoleRequest req = new ChangeRoleRequest(WorkerRole.ANALYST);
        WorkerResponse expected = sampleWorker();
        when(workerService.changeRole(id, req)).thenReturn(expected);
        assertThat(controller.changeRole(id, req).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void remove_returns204() {
        UUID id = UUID.randomUUID();
        doNothing().when(workerService).remove(id);
        assertThat(controller.remove(id).getStatusCode().value()).isEqualTo(204);
        verify(workerService).remove(id);
    }
}
