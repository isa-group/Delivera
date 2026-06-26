package com.delivera.auth.dto;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeliveraOrgContext {
        UUID companyId;
        String role;
        String companyName;
        String orgHandle;
        String orgName;
}
        
