package com.delivera.data.space.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.data.fms.dto.ClusterConfig;
import com.delivera.data.fms.dto.DbscanResult;
import com.delivera.data.fms.dto.DeliveryWindow;
import com.delivera.data.fms.dto.TypeSolver;
import com.delivera.data.space.dto.SpaceRequestJwt;
import com.delivera.data.space.service.SpaceGeneralService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/space")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Space", description = "Public endpoints to execute actions using space")
public class SpaceController {

    private final SpaceGeneralService spaceGeneral;

    private final SecurityUtils securityUtils;

    @Operation(summary = "Get space JWT ",
            description = "Obtain the related company's jwt")
    @GetMapping("/jwt")
    public ResponseEntity<SpaceRequestJwt> jwt(
            @Parameter(description = "Use server evaluation")
            @RequestParam(defaultValue = "false") boolean serverEvalutaion
    ) {
        UUID orgId = securityUtils.getCurrentOrgId();
       
        
        SpaceRequestJwt jwt = spaceGeneral.getToken(orgId.toString(), serverEvalutaion);
        return ResponseEntity.ok(jwt);
    }

}
