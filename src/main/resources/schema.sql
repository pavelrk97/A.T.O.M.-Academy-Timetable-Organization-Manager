-- ===============================
-- Таблица пользователей
-- ===============================
CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    full_name VARCHAR(255),
    email VARCHAR(255) UNIQUE,
    role VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    version BIGINT
);

-- ===============================
-- Таблица групп
-- ===============================
CREATE TABLE groups (
    id UUID PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    course INT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    version BIGINT
);

-- ===============================
-- Связь пользователей и групп (STUDENT)
-- ===============================
CREATE TABLE user_groups (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE
);

-- ===============================
-- Таблица дней
-- ===============================
CREATE TABLE days (
    id UUID PRIMARY KEY,
    date DATE NOT NULL,
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    version BIGINT
);

-- ===============================
-- Таблица уроков
-- ===============================
CREATE TABLE lessons (
    id UUID PRIMARY KEY,
    subject VARCHAR(255) NOT NULL,
    lesson_type VARCHAR(50) NOT NULL,
    start_time TIME,
    end_time TIME,
    room VARCHAR(255),
    day_id UUID NOT NULL REFERENCES days(id) ON DELETE CASCADE,
    created_by UUID REFERENCES users(id),
    updated_by UUID REFERENCES users(id),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    version BIGINT
);

-- ===============================
-- Связь many-to-many: уроки ↔ преподаватели
-- ===============================
CREATE TABLE lesson_lecturers (
    lesson_id UUID NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (lesson_id, user_id)
);

-- ===============================
-- Таблица уведомлений
-- ===============================
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(50),
    message TEXT,
    is_read BOOLEAN DEFAULT FALSE,
    related_entity_id UUID,
    created_at TIMESTAMP
);

-- ===============================
-- Таблица истории изменений (Audit / History)
-- ===============================
CREATE TABLE audit_history (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(255) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL, -- CREATE, UPDATE, DELETE
    changed_by UUID REFERENCES users(id),
    changed_at TIMESTAMP,
    old_value JSON,
    new_value JSON
);
