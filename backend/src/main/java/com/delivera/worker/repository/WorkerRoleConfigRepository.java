package com.delivera.worker.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.delivera.worker.model.WorkerRoleConfig;

public interface WorkerRoleConfigRepository extends JpaRepository<WorkerRoleConfig, String> {
}
