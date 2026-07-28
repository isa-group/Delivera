package com.delivera.data.activity.dto;

import java.time.LocalDate;

public record OrdersByDayEntry(LocalDate date, long count) {}
