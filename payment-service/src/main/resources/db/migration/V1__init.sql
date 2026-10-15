create table payments (
    id              bigserial primary key,
    from_account    varchar(255)             not null,
    to_account      varchar(255)             not null,
    amount          numeric(19, 4)           not null,
    status          varchar(255)             not null,
    idempotency_key varchar(64)              not null,
    created_at      timestamp with time zone not null,
    updated_at      timestamp with time zone not null,
    constraint uk_payments_idempotency_key unique (idempotency_key)
);
