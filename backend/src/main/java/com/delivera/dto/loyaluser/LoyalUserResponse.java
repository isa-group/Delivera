package com.delivera.dto.loyaluser;

import com.delivera.model.LoyalUser;
import com.delivera.model.LoyalUserCompany;
import com.delivera.model.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LoyalUserResponse(
        UUID id,
        String email,
        String name,
        String phone,
        boolean registered,
        long orderCount,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant createdAt) {

    public static LoyalUserResponse from(LoyalUser lu, LoyalUserCompany link) {
        return from(lu, link, 0);
    }

    public static LoyalUserResponse from(LoyalUser lu, LoyalUserCompany link, long orderCount) {
        String name = link != null ? link.getName() : null;
        String phone = link != null ? link.getPhone() : null;
        String address = link != null ? link.getAddress() : null;
        BigDecimal lat = link != null ? link.getLatitude() : null;
        BigDecimal lon = link != null ? link.getLongitude() : null;
        if (lu.getUser() != null) {
            User u = lu.getUser();
            name    = name    != null ? name    : resolveFullName(u);
            phone   = phone   != null ? phone   : u.getPhone();
            address = address != null ? address : u.getAddress();
            lat     = lat     != null ? lat     : u.getLatitude();
            lon     = lon     != null ? lon     : u.getLongitude();
        }
        Instant createdAt = link != null && link.getCreatedAt() != null ? link.getCreatedAt() : lu.getCreatedAt();
        return new LoyalUserResponse(lu.getId(), lu.getEmail(), name, phone,
                lu.getUser() != null, orderCount, address, lat, lon, createdAt);
    }

    private static String resolveFullName(User user) {
        if (user.getFirstName() == null) return null;
        String lastName = user.getLastName();
        String suffix = lastName != null ? " " + lastName : "";
        return (user.getFirstName() + suffix).trim();
    }
}
