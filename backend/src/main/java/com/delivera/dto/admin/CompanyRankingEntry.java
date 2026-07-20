package com.delivera.dto.admin;

import java.util.UUID;

public record CompanyRankingEntry(
        UUID companyId,
        String companyName,
        String orgName,
        long orderCount
) {}
