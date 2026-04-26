create table user_login_events (
    id uuid primary key,
    user_id uuid not null,
    logged_at timestamp not null default current_timestamp,
    ip_address varchar(64),
    user_agent varchar(512),
    constraint fk_user_login_events_user foreign key (user_id) references users(id) on delete cascade
);

create index idx_user_login_events_user_id on user_login_events(user_id);
create index idx_user_login_events_logged_at on user_login_events(logged_at);
