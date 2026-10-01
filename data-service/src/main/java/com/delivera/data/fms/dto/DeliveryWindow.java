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

    public  static  DeliveryWindow allOrders() {
        DeliveryWindow window = new  DeliveryWindow();

        window.setIncludeNullsFromDate(true);
        window.setIncludeNullsToDate(true);

        return  window;
    }

}
