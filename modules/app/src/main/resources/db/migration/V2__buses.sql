-- Adds the buses table.
--
-- Mirrors modules/buses/.../model/Tables.scala column-for-column and
-- constraint-for-constraint, same convention as V1__initial_schema.sql.
--
-- plate_number uniqueness is scoped to (school_id, plate_number), not
-- plate_number alone - see BusService's doc comment on why plate
-- uniqueness is per-school, unlike users.email's global uniqueness.

create table buses (
    id           uuid primary key,
    school_id    uuid not null references schools (id),
    plate_number text not null,
    capacity     int not null,
    status       text not null check (status in ('ACTIVE', 'INACTIVE', 'MAINTENANCE')),
    created_at   timestamptz not null,
    updated_at   timestamptz not null,
    unique (school_id, plate_number)
);
create index buses_school_id_idx on buses (school_id);
