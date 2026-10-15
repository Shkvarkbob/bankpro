create table accounts (
    id             bigserial primary key,
    account_number varchar(34)    not null,
    customer_id    bigint         not null,
    balance        numeric(19, 4) not null,
    version        bigint,
    constraint uk_accounts_account_number unique (account_number),
    constraint ck_accounts_balance_non_negative check (balance >= 0)
);

create table account_operations (
    operation_key varchar(68) primary key,
    processed_at  timestamp with time zone not null
);
