package com.delivera.data.fms.dto;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

@Getter 
@Setter 
public class DeliveryWindow {

    private  boolean includeNullsFromDate;

    private  boolean includeNullsToDate;

    private  Instant fromDate;

    private  Instant toDate;

    

}
