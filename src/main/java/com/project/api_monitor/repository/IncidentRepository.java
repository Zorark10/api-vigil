package com.project.api_monitor.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.project.api_monitor.model.Incident;
import com.project.api_monitor.model.IncidentStatus;
import com.project.api_monitor.model.Monitor;

public interface IncidentRepository extends JpaRepository<Incident, Integer>{
	public Optional<Incident> findByMonitorAndStatus(Monitor monitor, IncidentStatus status);

	public Page<Incident> findByMonitorOrderByStartedAtDesc(Monitor monitor, Pageable pageable);

	public Page<Incident> findByStatusOrderByStartedAtDesc(IncidentStatus status, Pageable pageable);
}
