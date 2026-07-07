package com.delivera.auth.dto;

import java.util.UUID;

import com.delivera.worker.model.WorkerRole;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class DeliveraOrgContext {
        UUID companyId;
        WorkerRole role;
        String companyName;
        String orgHandle;
        String orgName;
}
        
