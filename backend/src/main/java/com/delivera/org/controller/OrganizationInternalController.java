package com.delivera.org.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/organizations")
@Tag(name = "Organizations", description = "Internal organization endpoints")
public class OrganizationInternalController {

}
