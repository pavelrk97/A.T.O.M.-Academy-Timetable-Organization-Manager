create table if not exists users (
    id uuid primary key,
    version bigint,
    created_at timestamp(6),
    updated_at timestamp(6),
    username varchar(255) not null unique,
    password varchar(255) not null,
    full_name varchar(255),
    display_name varchar(255),
    email varchar(255),
    phone varchar(255),
    position varchar(255),
    department varchar(255),
    role varchar(32) not null,
    is_active boolean not null default true,
    can_teach boolean not null default true,
    constraint chk_users_role
        check (role in ('ADMIN', 'EDITOR', 'INSTRUCTOR'))
);

create table if not exists groups (
    id uuid primary key,
    version bigint,
    created_at timestamp(6),
    updated_at timestamp(6),
    code varchar(255) not null unique,
    location varchar(255),
    course integer
);

create table if not exists days (
    id uuid primary key,
    version bigint,
    created_at timestamp(6),
    updated_at timestamp(6),
    date date,
    group_id uuid not null,
    constraint fk_days_group
        foreign key (group_id) references groups(id) on delete cascade
);

create table if not exists day_meta (
    day_id uuid not null,
    meta_key varchar(255) not null,
    meta_value varchar(255),
    primary key (day_id, meta_key),
    constraint fk_day_meta_day
        foreign key (day_id) references days(id) on delete cascade
);

create table if not exists lessons (
    id uuid primary key,
    version bigint,
    created_at timestamp(6),
    updated_at timestamp(6),
    order_number integer not null,
    title varchar(255) not null,
    lecturer varchar(255),
    duration_hours integer not null,
    note varchar(255),
    type varchar(32) not null,
    day_id uuid not null,
    constraint fk_lessons_day
        foreign key (day_id) references days(id) on delete cascade,
    constraint chk_lessons_type
        check (type in ('LECTURE', 'SELF_STUDY', 'ASSESSMENT'))
);

create table if not exists lesson_lecturers (
    lesson_id uuid not null,
    lecturer_name varchar(255) not null,
    primary key (lesson_id, lecturer_name),
    constraint fk_lesson_lecturers_lesson
        foreign key (lesson_id) references lessons(id) on delete cascade
);

create table if not exists lesson_instructors (
    lesson_id uuid not null,
    user_id uuid not null,
    primary key (lesson_id, user_id),
    constraint fk_lesson_instructors_lesson
        foreign key (lesson_id) references lessons(id) on delete cascade,
    constraint fk_lesson_instructors_user
        foreign key (user_id) references users(id) on delete cascade
);

create table if not exists user_groups (
    user_id uuid not null,
    group_id uuid not null,
    primary key (user_id, group_id),
    constraint fk_user_groups_user
        foreign key (user_id) references users(id) on delete cascade,
    constraint fk_user_groups_group
        foreign key (group_id) references groups(id) on delete cascade
);

create table if not exists change_logs (
    id uuid primary key,
    version bigint,
    created_at timestamp(6),
    updated_at timestamp(6),
    entity_type varchar(255) not null,
    entity_id uuid not null,
    action varchar(32) not null,
    changed_by varchar(255) not null,
    before_json text,
    after_json text,
    comment text,
    constraint chk_change_logs_action
        check (action in ('CREATED', 'UPDATED', 'DELETED'))
);

create table if not exists notifications (
    id uuid primary key,
    version bigint,
    created_at timestamp(6),
    updated_at timestamp(6),
    user_id uuid not null,
    type varchar(32),
    message varchar(255),
    is_read boolean not null default false,
    related_entity_id uuid,
    constraint fk_notifications_user
        foreign key (user_id) references users(id) on delete cascade,
    constraint chk_notifications_type
        check (type in ('LESSON_ADDED', 'LESSON_CANCELLED'))
);

create index if not exists idx_days_group_id on days(group_id);
create index if not exists idx_lessons_day_id on lessons(day_id);
create index if not exists idx_change_logs_entity_id on change_logs(entity_id);
create index if not exists idx_notifications_user_id on notifications(user_id);
