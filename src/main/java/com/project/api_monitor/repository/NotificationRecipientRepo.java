package com.project.api_monitor.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.api_monitor.model.NotificationRecipient;

public interface NotificationRecipientRepo extends JpaRepository<NotificationRecipient, Integer> {

	List<NotificationRecipient> findAllByOwnerIdOrderByIdAsc(Integer ownerId);

	boolean existsByOwnerIdAndEmail(Integer ownerId, String email);

	Optional<NotificationRecipient> findByIdAndOwnerId(Integer id, Integer ownerId);

	boolean existsByIdAndOwnerId(Integer id, Integer ownerId);
}
