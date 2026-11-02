package com.project.api_monitor.model;

/**
 * Public bootstrap status: tells the registration page whether the instance
 * still has no users at all, in which case the account it creates will become
 * the initial administrator. Exposes no other information.
 */
public record SetupStatusResponse(boolean administratorRequired) {
}
