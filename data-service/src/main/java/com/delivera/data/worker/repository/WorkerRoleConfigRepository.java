package com.delivera.data.worker.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.delivera.data.worker.model.WorkerRoleConfig;


public interface WorkerRoleConfigRepository extends JpaRepository<WorkerRoleConfig, String> {
}
