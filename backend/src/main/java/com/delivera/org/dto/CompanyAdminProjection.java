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


    public InnerCompanyAdminProjection coreData() {
        return new InnerCompanyAdminProjection(companyName, orgName);
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class InnerCompanyAdminProjection {
        private String companyName;
        private String orgName;

        
    }



}
