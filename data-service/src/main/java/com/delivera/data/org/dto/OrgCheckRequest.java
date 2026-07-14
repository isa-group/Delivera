package com.delivera.data.org.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class OrgCheckRequest {
    UUID companyId;
    UUID orgId;
}
