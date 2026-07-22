package com.delivera.data.org.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;


import com.delivera.data.org.model.CompanySettings;

public interface SettingsRepository extends JpaRepository<CompanySettings, UUID> {

}
