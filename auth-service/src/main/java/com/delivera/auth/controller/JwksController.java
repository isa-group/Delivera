package com.delivera.auth.controller;


import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.delivera.auth.security.jwt.JwkProvider;

@RestController
@RequestMapping("/.well-known")
public class JwksController {

    private final JwkProvider jwkProvider;

    @Autowired
    public JwksController(JwkProvider jwkProvider) {
        this.jwkProvider = jwkProvider;
    }

    @GetMapping("/jwks.json")
    public Map<String, Object> getKeys() {
        return jwkProvider.getJwkSet().toJSONObject();
    }
}
