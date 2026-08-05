package com.delivera.data.order.dto;


public record RefreshCookieData(
    String token,
    Boolean secureRefreshCookie,
    String domainRefreshCookie,
    String pathRefreshCookie,
    Integer daysToRefresh
){}
