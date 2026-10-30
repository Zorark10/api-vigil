package com.project.api_monitor.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.api_monitor.model.NotificationRecipient;

public interface NotificationRecipientRepo extends JpaRepository<NotificationRecipient, Integer> {

	List<NotificationRecipient> findAllByOrderByIdAsc();

	long countByEnabledTrue();

	boolean existsByEmail(String email);
}
