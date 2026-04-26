create table auto_import_settings (
    id bigint primary key,
    enabled boolean not null default false,
    source_url text not null,
    last_run_at timestamp,
    last_status varchar(32),
    last_error text,
    last_imported_groups integer,
    last_imported_lessons integer,
    updated_at timestamp not null default current_timestamp,
    updated_by varchar(255)
);

insert into auto_import_settings (id, enabled, source_url)
values (
    1,
    false,
    'https://docs.google.com/spreadsheets/d/1Cn9AwN2O_qo0CTUXqrYML1Lh6tm7br_4yzuLhqtL5yI/edit?gid=1705623528'
);
