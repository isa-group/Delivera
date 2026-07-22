package com.delivera.data.common.dto;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class User {

  
    private UUID id;


    private String email;


    private String username;



    private String firstName;


    private String lastName;


    private String phone;


    private String address;


    private BigDecimal latitude;


    private BigDecimal longitude;

    private Instant createdAt;

 
    @Column(nullable = false)
    private boolean invited = false;


    private String avatarData;


}
