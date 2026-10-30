package com.project.api_monitor.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.api_monitor.model.NotificationSettings;

public interface NotificationSettingsRepo extends JpaRepository<NotificationSettings, Integer> {
}
