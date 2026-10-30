package com.project.api_monitor.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.api_monitor.model.AppUser;

public interface AppUserRepo extends JpaRepository<AppUser, Integer> {

	Optional<AppUser> findByUsername(String username);
}
