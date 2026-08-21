-- Initial schema for the schoolbus system.
--
-- Mirrors the Slick table definitions in modules/{auth,schools,students}/.../model/Tables.scala
-- column-for-column and constraint-for-constraint - anything not modeled in
-- Scala today (route bus/shift assignment, route stops, school timezone,
-- parent/student relationship labels) is deliberately left out rather than
-- added as dead schema. Add those columns/tables in the same migration that
-- adds the corresponding Scala model.
--
-- role/shift-style enum columns use a CHECK constraint instead of a Postgres
-- ENUM type, so adding a new role later is a migration that alters a CHECK,
-- not one that alters a type used across several columns.

create table schools (
    id         uuid primary key,
    name       text not null,
    is_active  boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table users (
    id            uuid primary key,
    school_id     uuid null references schools (id),
    -- Global uniqueness, not per-school - see UserRepository.findByEmail,
    -- which looks up by email alone with no school scope.
    email         text not null unique,
    password_hash text not null,
    role          text not null check (role in ('SUPER_ADMIN', 'SCHOOL_ADMIN', 'DRIVER', 'PARENT')),
    is_active     boolean not null default true,
    created_at    timestamptz not null,
    updated_at    timestamptz not null
);
create index users_school_id_idx on users (school_id);

create table refresh_tokens (
    id         uuid primary key,
    user_id    uuid not null references users (id),
    -- Unique because RefreshTokenRepository.findByHash looks a token up by
    -- its hash alone; a collision here would mean one refresh token
    -- resolving to two different sessions.
    token_hash text not null unique,
    expires_at timestamptz not null,
    revoked_at timestamptz null,
    created_at timestamptz not null
);
create index refresh_tokens_user_id_idx on refresh_tokens (user_id);

create table password_reset_tokens (
    id         uuid primary key,
    user_id    uuid not null references users (id),
    token_hash text not null unique,
    expires_at timestamptz not null,
    used_at    timestamptz null,
    created_at timestamptz not null
);
create index password_reset_tokens_user_id_idx on password_reset_tokens (user_id);

create table routes (
    id         uuid primary key,
    school_id  uuid not null references schools (id),
    name       text not null,
    is_active  boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null
);
create index routes_school_id_idx on routes (school_id);

create table students (
    id         uuid primary key,
    school_id  uuid not null references schools (id),
    first_name text not null,
    last_name  text not null,
    grade      text not null,
    route_id   uuid null references routes (id) on delete set null,
    is_active  boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null
);
-- Covers SlickStudentRepository.scoped's (school_id, optional route_id)
-- filter shape; school_id-only lookups can still use the leading column.
create index students_school_id_route_id_idx on students (school_id, route_id);
create index students_route_id_idx on students (route_id);

create table student_parents (
    student_id      uuid not null references students (id),
    -- Not modeled as a Scala-level dependency on `auth` (students doesn't
    -- import auth's Slick tables - see StudentParentsTable's comment), but
    -- there's no reason for the *database* to leave this unconstrained: a
    -- real FK here can't create a Scala compile-time dependency, it just
    -- stops orphaned parent links.
    parent_user_id  uuid not null references users (id),
    created_at      timestamptz not null,
    primary key (student_id, parent_user_id)
);
create index student_parents_parent_user_id_idx on student_parents (parent_user_id);
