package com.delivera.data.activity.dto;

import java.util.UUID;

public record UnitRankingEntry(UUID unitId, String unitName, String unitType, long orderCount) {}
