package com.demo.authservice.user.dto;

/** The token from a signup link plus the password chosen at signup: the inbox and the password, together. */
public record EmailVerification(String token, String password) {}
