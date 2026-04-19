alter table users
    add column if not exists editor_access boolean not null default false;
