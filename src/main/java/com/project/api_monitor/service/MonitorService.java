package com.project.api_monitor.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.api_monitor.exception.MonitorNotFoundException;
import com.project.api_monitor.model.AppUser;
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

	/**
	 * Every monitor belongs to a workspace (owner account). Ownership comes
	 * from the authenticated principal server-side; all reads, updates and
	 * deletes look up the monitor by id AND owner, so another account's
	 * monitor is indistinguishable from an unknown id (404 — no leaking).
	 */
	public Monitor addMonitor(Monitor monitor, AppUser owner) {
		monitor.setOwner(owner);
		monitor.setCreatedAt(LocalDateTime.now());
		return monitorRepo.save(monitor);
	}

	public List<Monitor> getAllMonitors(AppUser owner) {
		return monitorRepo.findByOwnerOrderByIdAsc(owner);
	}

	public Monitor getMonitorById(Integer id, AppUser owner) {
		return monitorRepo.findByIdAndOwnerId(id, owner.getId())
				.orElseThrow(() -> new MonitorNotFoundException(id));
	}

	public Monitor updateMonitor(Integer id, Monitor monitor, AppUser owner) {
		Monitor updated = monitorRepo.findByIdAndOwnerId(id, owner.getId())
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
	 * produced for a monitor the operator removed on purpose. Unknown ids and
	 * other accounts' ids both answer 404. The Redis status/scheduling
	 * entries are cleared best-effort afterwards; a cache failure must not
	 * undo the deletion.
	 */
	@Transactional
	public void deleteMonitor(Integer id, AppUser owner) {
		Monitor monitor = monitorRepo.findByIdAndOwnerId(id, owner.getId())
				.orElseThrow(() -> new MonitorNotFoundException(id));
		monitorResultRepo.deleteByMonitor(monitor);
		incidentRepo.deleteByMonitor(monitor);
		monitorRepo.delete(monitor);
		redisSchedulerService.deleteMonitorCache(id);
	}

}
