package com.project.api_monitor.repository;


import java.util.List;
import java.util.Optional;


import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;

import com.project.api_monitor.model.Monitor;
import com.project.api_monitor.model.MonitorResult;

public interface MonitorResultRepo extends JpaRepository<MonitorResult, Integer>{
	public List<MonitorResult> findByMonitor(Monitor monitor);

	public Optional<MonitorResult> findFirstByMonitorOrderByCheckedAtDesc(Monitor monitor);

	public Page<MonitorResult> findByMonitor(Monitor monitor, Pageable pageable);

	public void deleteByMonitor(Monitor monitor);
}
