package com.delivera.org.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CompanyAdminProjection {

    private UUID companyId;

    private String companyName;

    private String orgName;




}
