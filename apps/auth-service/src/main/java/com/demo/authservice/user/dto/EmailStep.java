package com.demo.authservice.user.dto;

/**
 * What a change-of-email link did: {@code changed} and the address now held, or not yet changed and the new
 * address the next link went to (the first of a provider-only user's two steps).
 */
public record EmailStep(String email, boolean changed) {}
