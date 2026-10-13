--liquibase formatted sql

-- Whether the user proved it owns its email: by a mailed link at signup, or by a provider that verified it. An
-- unverified user cannot sign in, and a provider sign-in for its address claims it. Rows from before this column
-- default to true: they cannot be asked after the fact, and false would lock every existing user out.
--changeset auth-service:3-email-verified
alter table users add column email_verified boolean not null default true;
