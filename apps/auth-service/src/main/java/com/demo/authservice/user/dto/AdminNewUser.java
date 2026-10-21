package com.demo.authservice.user.dto;

import com.demo.authservice.user.entities.Role;

/** An admin's new user. The one way in that may name a role; null means USER. */
public record AdminNewUser(String name, String email, String password, Role role) {}
