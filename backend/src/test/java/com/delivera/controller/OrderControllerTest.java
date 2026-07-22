package com.delivera.controller;

import com.delivera.auth.dto.ClaimData;
import com.delivera.auth.service.AuthService;
import com.delivera.dto.auth.ClaimRegisterRequest;
import com.delivera.dto.auth.LoginResponse;
import com.delivera.order.controller.OrderController;
import com.delivera.order.dto.OrderDetailResponse;
import com.delivera.order.dto.OrderRequest;
import com.delivera.order.dto.OrderResponse;
import com.delivera.security.AuthRateLimiter;
import com.delivera.order.dto.OrderStatusRequest;
import com.delivera.order.dto.PublicOrderResponse;
import com.delivera.order.model.OrderStatus;
import com.delivera.order.model.OrderType;
import com.delivera.order.service.OrderService;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock private OrderService orderService;
    @Mock private AuthService authService;
    @Mock private AuthRateLimiter authRateLimiter;
    @Mock private HttpServletRequest httpRequest;
    @InjectMocks private OrderController controller;

    private static OrderResponse sampleResponse() {
        return new OrderResponse(UUID.randomUUID(), null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, false, null, null, null, null, null, null, null, null, null);
    }

    private static OrderDetailResponse sampleDetail(UUID id) {
        return new OrderDetailResponse(id, null, null, null, null, null, null,
                null, null, null, null, null, null, null, "PENDING", null, null, false, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    @Test
    void list_delegatesAndReturns200() {
        when(orderService.getByCompany()).thenReturn(List.of(sampleResponse()));
        var resp = controller.list();
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
        verify(orderService).getByCompany();
    }

    @Test
    void detail_delegatesAndReturns200() {
        UUID id = UUID.randomUUID();
        OrderDetailResponse expected = sampleDetail(id);
        when(orderService.getDetail(id)).thenReturn(expected);
        var resp = controller.detail(id);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isSameAs(expected);
    }
/* TODO
    @Test
    void create_delegatesAndReturns201() {
        OrderRequest req = new OrderRequest(UUID.randomUUID(), null, "a@b.com", "John",
                "street", null, null, OrderType.B2C, null, null);
        OrderResponse expected = sampleResponse();
        when(orderService.create(req)).thenReturn(expected);
        var resp = controller.create(req);
        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getBody()).isSameAs(expected);
    }
*/
    @Test
    void updateStatus_delegatesAndReturns200() {
        UUID id = UUID.randomUUID();
        OrderStatusRequest req = new OrderStatusRequest(OrderStatus.IN_TRANSIT, "moving");
        OrderDetailResponse expected = sampleDetail(id);
        when(orderService.updateStatus(id, req)).thenReturn(expected);
        var resp = controller.updateStatus(id, req);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isSameAs(expected);
    }

    @Test
    void delete_delegatesAndReturns204() {
        UUID id = UUID.randomUUID();
        doNothing().when(orderService).delete(id);
        var resp = controller.delete(id);
        assertThat(resp.getStatusCode().value()).isEqualTo(204);
        verify(orderService).delete(id);
    }

    @Test
    void trackByToken_delegatesAndReturns200() {
        // PublicOrderResponse(id, reference, companyName, originName, destinationName, recipientName,
        //   recipientAddress, status, priority, createdAt, events, claimed, trackingToken,
        //   destinationLat, destinationLon, currentLat, currentLon)
        PublicOrderResponse pub = new PublicOrderResponse(UUID.randomUUID(), "REF-1", null, null, null,
                null, null, "PENDING", null, null, null, false, "tok", null, null, null, null);
        when(orderService.getPublicByToken("tok")).thenReturn(pub);
        var resp = controller.trackByToken("tok");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isSameAs(pub);
    }

    @Test
    void trackByReference_delegatesAndReturns200() {
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        PublicOrderResponse pub = new PublicOrderResponse(UUID.randomUUID(), "REF-2", null, null, null,
                null, null, "DELIVERED", null, null, null, false, null, null, null, null, null);
        when(orderService.getPublicByReference("REF-2")).thenReturn(pub);
        var resp = controller.trackByReference(httpRequest, "REF-2");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }
 
    /*  TODO: .....
    @Test
    void claimRegister_delegatesAndReturns201() {
        // ClaimRegisterRequest(firstName, lastName, email, username, password)
        ClaimRegisterRequest req = new ClaimRegisterRequest("First", "Last", "a@b.com", "firstlast", "Pass1a2B");
        // LoginResponse(token, email, companyId, role, companyName, orgHandle, orgName)
        LoginResponse expected = new LoginResponse("jwt-token", "a@b.com", null, "LOYAL_USER", null, null, null,null,null);
        when(authService.claimRegister(eq("tok"), eq(req),any())).thenReturn(expected);
        when(authService.refreshCookie(any())).thenReturn(ResponseCookie.from("saasa").build());
        var resp = controller.claimRegister(any(),"tok", req);
           
        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getBody()).isSameAs(expected);
    }*/
}
