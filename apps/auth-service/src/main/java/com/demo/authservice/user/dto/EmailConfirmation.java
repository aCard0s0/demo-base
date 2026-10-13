package com.demo.authservice.user.dto;

/** The token from a confirmation link, posted by the frontend's confirm page. */
public record EmailConfirmation(String token) {}
