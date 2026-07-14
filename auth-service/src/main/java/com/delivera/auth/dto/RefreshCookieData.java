package com.delivera.auth.dto;


public record RefreshCookieData(
    String token,
    Boolean secureRefreshCookie,
    String domainRefreshCookie,
    String pathRefreshCookie,
    Integer daysToRefresh
){}
