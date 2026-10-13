--liquibase formatted sql

-- Whether the user ever chose a password. A user made by signing in with a provider holds a hash nobody knows,
-- so the profile cannot ask it for a "current password". Rows from before this column default to true: there is
-- no telling the two kinds apart after the fact, and true keeps them exactly as locked as they were.
--changeset auth-service:2-password-set
alter table users add column password_set boolean not null default true;
