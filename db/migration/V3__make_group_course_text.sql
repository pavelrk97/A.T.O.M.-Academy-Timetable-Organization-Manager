alter table groups
    alter column course type varchar(255)
    using course::varchar;
