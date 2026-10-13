package com.demo.authservice.user.dto;

/** An email address: the one asked for on the way in, and the one written to (or now held) on the way out. */
public record EmailChange(String email) {}
