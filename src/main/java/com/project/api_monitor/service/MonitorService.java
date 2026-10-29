package com.project.api_monitor.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.project.api_monitor.exception.MonitorNotFoundException;
import com.project.api_monitor.model.Monitor;
import com.project.api_monitor.repository.MonitorRepo;

@Service
public class MonitorService {
	
	@Autowired
	private MonitorRepo monitorRepo;
	
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

	public void deleteMonitor(Integer id) {
		Monitor monitor = monitorRepo.findById(id)
				.orElseThrow(() -> new MonitorNotFoundException(id));
		monitorRepo.delete(monitor);
	}
	
}
