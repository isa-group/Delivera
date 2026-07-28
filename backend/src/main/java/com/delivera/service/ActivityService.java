package com.delivera.service;

import com.delivera.dto.activity.ActivityMetricsResponse;
import com.delivera.repository.LoyalUserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivityService {

  
 
    private final LoyalUserRepository loyalUserRepository;

    @Transactional(readOnly = true)
    public ActivityMetricsResponse getMetrics(UUID companyId, String period) {
        Instant from = periodStart(period);
        return new ActivityMetricsResponse(
                period,
                loyalUserRepository.countByCompanyIdAndLinkCreatedAfter(companyId, from)
        );
    }


    private Instant periodStart(String period) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return switch (period) {
            case "TODAY" -> today.atStartOfDay().toInstant(ZoneOffset.UTC);
            case "WEEK"  -> today.with(DayOfWeek.MONDAY).atStartOfDay().toInstant(ZoneOffset.UTC);
            default      -> today.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(ZoneOffset.UTC);
        };
    }
}
