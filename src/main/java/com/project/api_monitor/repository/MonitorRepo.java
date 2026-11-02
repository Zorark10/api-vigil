package com.project.api_monitor.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.api_monitor.model.AppUser;
import com.project.api_monitor.model.Monitor;

public interface MonitorRepo extends JpaRepository<Monitor, Integer> {

	// Workspace-scoped reads: only the owner sees and manages its monitors.
	List<Monitor> findByOwnerOrderByIdAsc(AppUser owner);

	Optional<Monitor> findByIdAndOwnerId(Integer id, Integer ownerId);

	// Unscoped findById is still used by the Kafka pipeline (scheduler,
	// result consumer), which has no HTTP authentication context.
}
