package com.project.api_monitor.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.api_monitor.exception.MonitorNotFoundException;
import com.project.api_monitor.model.Monitor;
import com.project.api_monitor.repository.IncidentRepository;
import com.project.api_monitor.repository.MonitorRepo;
import com.project.api_monitor.repository.MonitorResultRepo;

@Service
public class MonitorService {

	@Autowired
	private MonitorRepo monitorRepo;

	@Autowired
	private MonitorResultRepo monitorResultRepo;

	@Autowired
	private IncidentRepository incidentRepo;

	@Autowired
	private RedisSchedulerService redisSchedulerService;

	public Monitor addMonitor(Monitor monitor) {
		monitor.setCreatedAt(LocalDateTime.now());
		return monitorRepo.save(monitor);
	}

	public List<Monitor> getAllMonitors(){
		return monitorRepo.findAll();
	}

	public Monitor getMonitorById(Integer id) {
		return monitorRepo.findById(id).orElseThrow(() -> new MonitorNotFoundException(id));
	}

	public Monitor updateMonitor(Integer id, Monitor monitor) {
		Monitor updated = monitorRepo.findById(id)
				.orElseThrow(() -> new MonitorNotFoundException(id));
		updated.setUrl(monitor.getUrl());
		updated.setName(monitor.getName());
		updated.setHttpMethod(monitor.getHttpMethod());
		updated.setInterval(monitor.getInterval());
		updated.setTimeout(monitor.getTimeout());
		updated.setEnabled(monitor.isEnabled());

		return monitorRepo.save(updated);
	}

	/**
	 * Deletes a monitor together with its check results and incidents in one
	 * transaction, so the referenced rows never outlive the monitor and a
	 * failure cannot leave a partially deleted monitor behind. Incidents are
	 * removed silently: no recovery email and no incident-resolution event is
	 * produced for a monitor the operator removed on purpose. Unknown ids keep
	 * the usual 404. The Redis status/scheduling entries are cleared
	 * best-effort afterwards; a cache failure must not undo the deletion, and
	 * any later read re-resolves against the database (404 for a deleted
	 * monitor).
	 */
	@Transactional
	public void deleteMonitor(Integer id) {
		Monitor monitor = monitorRepo.findById(id)
				.orElseThrow(() -> new MonitorNotFoundException(id));
		monitorResultRepo.deleteByMonitor(monitor);
		incidentRepo.deleteByMonitor(monitor);
		monitorRepo.delete(monitor);
		redisSchedulerService.deleteMonitorCache(id);
	}

}
