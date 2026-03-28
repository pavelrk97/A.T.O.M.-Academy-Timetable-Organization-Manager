const fallbackConfig = {
    baseUrl: '',
    admin: {
        username: 'admin',
        password: 'admin123'
    },
    instructor: {
        username: 'instructor',
        password: 'instructor123'
    },
    publicSchedule: {
        groupCode: 'гр.6 ()',
        from: '2026-01-05',
        to: '2026-01-05'
    },
    cabinet: {
        from: '2026-01-01',
        to: '2026-12-31'
    },
    profileTemplate: {
        fullName: 'Меняйло',
        email: 'instructor+cabinet@atom.local',
        phone: '+7-999-100-20-30',
        position: 'Ведущий инструктор',
        department: 'Кафедра автоматизации'
    },
    userTemplate: {
        username: 'demo.editor',
        password: 'demo123',
        fullName: 'Demo Editor',
        email: 'demo.editor@atom.local',
        phone: '',
        position: '',
        department: '',
        role: 'EDITOR',
        active: true,
        canTeach: false
    },
    lessonTemplate: {
        orderNumber: 1,
        title: 'Demo lesson',
        durationHours: 2,
        note: '',
        type: 'LECTURE'
    }
};

function cloneConfig(config) {
    return JSON.parse(JSON.stringify(config));
}

const state = {
    config: cloneConfig(fallbackConfig),
    lastLessonId: null,
    lastAction: '-',
    users: [],
    groups: []
};

const refs = {};
const VIEW_LIMITS = {
    rawPreviewChars: 16000,
    genericRows: 40,
    gridGroups: 18,
    gridDates: 18,
    lessonsPerCell: 3,
    scheduleRows: 80,
    workloadDays: 45,
    notifications: 40,
    stringPreviewChars: 140
};

document.addEventListener('DOMContentLoaded', () => {
    cacheDom();
    bindActions();
    renderStatus();
    renderProfileSummary();
    populateUserPicker([]);
    populateInstructorPicker([]);
    populateGroupPicker([]);
    setRawOutput({});
    loadConfig(false);
});

function cacheDom() {
    const ids = [
        'baseUrl',
        'adminUser',
        'adminPass',
        'instructorUser',
        'instructorPass',
        'groupCode',
        'publicFrom',
        'publicTo',
        'cabinetFrom',
        'cabinetTo',
        'profileFullName',
        'profileEmail',
        'profilePhone',
        'profilePosition',
        'profileDepartment',
        'currentPassword',
        'newPassword',
        'csvFile',
        'gatewayStatus',
        'lastAction',
        'lastLessonId',
        'profileSummary',
        'viewTitle',
        'viewHost',
        'logHost',
        'rawHost',
        'reloadConfigBtn',
        'exportConfigBtn',
        'importCsvBtn',
        'adminMeBtn',
        'usersBtn',
        'groupsBtn',
        'legacyWorkloadBtn',
        'lessonByIdBtn',
        'lessonHistoryBtn',
        'userPicker',
        'loadUsersBtn',
        'fillUserFormBtn',
        'editUserId',
        'editUsername',
        'editUserPassword',
        'editUserFullName',
        'editUserEmail',
        'editUserPhone',
        'editUserPosition',
        'editUserDepartment',
        'editUserRole',
        'editUserActive',
        'editUserCanTeach',
        'createUserBtn',
        'updateUserBtn',
        'lessonGroupPicker',
        'lessonDayPicker',
        'lessonInstructorPicker',
        'editLessonId',
        'editLessonVersion',
        'editLessonOrderNumber',
        'editLessonDurationHours',
        'editLessonTitle',
        'editLessonType',
        'editLessonNote',
        'loadLessonFormBtn',
        'createLessonBtn',
        'updateLessonBtn',
        'publicGroupBtn',
        'publicDayBtn',
        'loadProfileBtn',
        'saveProfileBtn',
        'changePasswordBtn',
        'dashboardBtn',
        'fullGridBtn',
        'instructorGridBtn',
        'workloadCalendarBtn',
        'notificationsBtn'
    ];

    ids.forEach((id) => {
        refs[id] = document.getElementById(id);
    });
}

function bindActions() {
    refs.reloadConfigBtn.addEventListener('click', () => loadConfig(true));
    refs.exportConfigBtn.addEventListener('click', exportConfig);

    refs.importCsvBtn.addEventListener('click', importCsv);
    refs.adminMeBtn.addEventListener('click', loadAdminMe);
    refs.usersBtn.addEventListener('click', loadUsers);
    refs.groupsBtn.addEventListener('click', loadGroups);
    refs.legacyWorkloadBtn.addEventListener('click', loadLegacyWorkload);
    refs.lessonByIdBtn.addEventListener('click', loadLessonById);
    refs.lessonHistoryBtn.addEventListener('click', loadLessonHistory);
    refs.loadUsersBtn.addEventListener('click', loadUsers);
    refs.fillUserFormBtn.addEventListener('click', fillUserFormFromPicker);
    refs.createUserBtn.addEventListener('click', createUser);
    refs.updateUserBtn.addEventListener('click', updateUser);
    refs.lessonGroupPicker.addEventListener('change', syncDayPickerFromSelectedGroup);
    refs.loadLessonFormBtn.addEventListener('click', loadLessonIntoForm);
    refs.createLessonBtn.addEventListener('click', createLesson);
    refs.updateLessonBtn.addEventListener('click', updateLesson);

    refs.publicGroupBtn.addEventListener('click', loadPublicScheduleByGroup);
    refs.publicDayBtn.addEventListener('click', loadPublicScheduleByDay);

    refs.loadProfileBtn.addEventListener('click', loadMyProfile);
    refs.saveProfileBtn.addEventListener('click', saveMyProfile);
    refs.changePasswordBtn.addEventListener('click', changeMyPassword);

    refs.dashboardBtn.addEventListener('click', loadDashboard);
    refs.fullGridBtn.addEventListener('click', loadFullGrid);
    refs.instructorGridBtn.addEventListener('click', loadInstructorGrid);
    refs.workloadCalendarBtn.addEventListener('click', loadWorkloadCalendar);
    refs.notificationsBtn.addEventListener('click', loadNotifications);
}

async function loadConfig(forceMessage) {
    let loadedConfig = cloneConfig(fallbackConfig);

    try {
        const response = await fetch('./config.json', {cache: 'no-store'});
        if (!response.ok) {
            throw new Error(`config.json -> ${response.status}`);
        }
        loadedConfig = mergeConfig(fallbackConfig, await response.json());
        log('config.json загружен, форму обновил.', 'success');
    } catch (error) {
        log('config.json не прочитался, оставил fallback прямо в app.js.', 'info');
    }

    state.config = loadedConfig;
    applyConfigToForm(loadedConfig);

    if (forceMessage) {
        log('Конфиг перечитан вручную.', 'info');
    }
}

function mergeConfig(baseConfig, loadedConfig) {
    return {
        ...baseConfig,
        ...loadedConfig,
        admin: {...baseConfig.admin, ...(loadedConfig.admin || {})},
        instructor: {...baseConfig.instructor, ...(loadedConfig.instructor || {})},
        publicSchedule: {...baseConfig.publicSchedule, ...(loadedConfig.publicSchedule || {})},
        cabinet: {...baseConfig.cabinet, ...(loadedConfig.cabinet || {})},
        profileTemplate: {...baseConfig.profileTemplate, ...(loadedConfig.profileTemplate || {})},
        userTemplate: {...baseConfig.userTemplate, ...(loadedConfig.userTemplate || {})},
        lessonTemplate: {...baseConfig.lessonTemplate, ...(loadedConfig.lessonTemplate || {})}
    };
}

function applyConfigToForm(config) {
    refs.baseUrl.value = config.baseUrl || '';
    refs.adminUser.value = config.admin.username || '';
    refs.adminPass.value = config.admin.password || '';
    refs.instructorUser.value = config.instructor.username || '';
    refs.instructorPass.value = config.instructor.password || '';

    refs.groupCode.value = config.publicSchedule.groupCode || '';
    refs.publicFrom.value = config.publicSchedule.from || '';
    refs.publicTo.value = config.publicSchedule.to || '';

    refs.cabinetFrom.value = config.cabinet.from || '';
    refs.cabinetTo.value = config.cabinet.to || '';

    refs.profileFullName.value = config.profileTemplate.fullName || '';
    refs.profileEmail.value = config.profileTemplate.email || '';
    refs.profilePhone.value = config.profileTemplate.phone || '';
    refs.profilePosition.value = config.profileTemplate.position || '';
    refs.profileDepartment.value = config.profileTemplate.department || '';

    refs.editUsername.value = config.userTemplate.username || '';
    refs.editUserPassword.value = config.userTemplate.password || '';
    refs.editUserFullName.value = config.userTemplate.fullName || '';
    refs.editUserEmail.value = config.userTemplate.email || '';
    refs.editUserPhone.value = config.userTemplate.phone || '';
    refs.editUserPosition.value = config.userTemplate.position || '';
    refs.editUserDepartment.value = config.userTemplate.department || '';
    refs.editUserRole.value = config.userTemplate.role || 'EDITOR';
    refs.editUserActive.checked = config.userTemplate.active !== false;
    refs.editUserCanTeach.checked = config.userTemplate.canTeach !== false;

    refs.editLessonOrderNumber.value = config.lessonTemplate.orderNumber ?? 1;
    refs.editLessonDurationHours.value = config.lessonTemplate.durationHours ?? 2;
    refs.editLessonTitle.value = config.lessonTemplate.title || '';
    refs.editLessonType.value = config.lessonTemplate.type || 'LECTURE';
    refs.editLessonNote.value = config.lessonTemplate.note || '';

    populateUserPicker(state.users);
    populateInstructorPicker(state.users);
    populateGroupPicker(state.groups);
}

function collectConfigFromForm() {
    return {
        baseUrl: refs.baseUrl.value.trim(),
        admin: {
            username: refs.adminUser.value.trim(),
            password: refs.adminPass.value
        },
        instructor: {
            username: refs.instructorUser.value.trim(),
            password: refs.instructorPass.value
        },
        publicSchedule: {
            groupCode: refs.groupCode.value.trim(),
            from: refs.publicFrom.value,
            to: refs.publicTo.value
        },
        cabinet: {
            from: refs.cabinetFrom.value,
            to: refs.cabinetTo.value
        },
        profileTemplate: readProfilePayload(),
        userTemplate: readUserPayload(),
        lessonTemplate: {
            orderNumber: Number(refs.editLessonOrderNumber.value || 0),
            title: refs.editLessonTitle.value.trim(),
            durationHours: Number(refs.editLessonDurationHours.value || 0),
            note: refs.editLessonNote.value.trim(),
            type: refs.editLessonType.value
        }
    };
}

function exportConfig() {
    const config = collectConfigFromForm();
    const blob = new Blob([JSON.stringify(config, null, 2)], {type: 'application/json'});
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = 'atom-test-ui.config.json';
    link.click();
    URL.revokeObjectURL(link.href);
    log('Скачал текущий config как JSON.', 'success');
}

function getBaseUrl() {
    return refs.baseUrl.value.trim().replace(/\/+$/, '');
}

function getAdminAuth() {
    return {
        username: refs.adminUser.value.trim(),
        password: refs.adminPass.value
    };
}

function getInstructorAuth() {
    return {
        username: refs.instructorUser.value.trim(),
        password: refs.instructorPass.value
    };
}

function readProfilePayload() {
    return {
        fullName: refs.profileFullName.value.trim(),
        email: refs.profileEmail.value.trim(),
        phone: refs.profilePhone.value.trim(),
        position: refs.profilePosition.value.trim(),
        department: refs.profileDepartment.value.trim()
    };
}

function readUserPayload() {
    return {
        username: refs.editUsername.value.trim(),
        password: refs.editUserPassword.value,
        fullName: refs.editUserFullName.value.trim(),
        email: refs.editUserEmail.value.trim(),
        phone: refs.editUserPhone.value.trim(),
        position: refs.editUserPosition.value.trim(),
        department: refs.editUserDepartment.value.trim(),
        role: refs.editUserRole.value,
        active: refs.editUserActive.checked,
        canTeach: refs.editUserCanTeach.checked
    };
}

function readLessonPayload() {
    const selectedGroup = getSelectedGroup();
    const dayId = refs.lessonDayPicker.value;
    const instructorIds = Array.from(refs.lessonInstructorPicker.selectedOptions)
        .map((option) => option.value)
        .filter(Boolean);

    return {
        id: refs.editLessonId.value || null,
        version: refs.editLessonVersion.value ? Number(refs.editLessonVersion.value) : null,
        orderNumber: Number(refs.editLessonOrderNumber.value || 0),
        title: refs.editLessonTitle.value.trim(),
        durationHours: Number(refs.editLessonDurationHours.value || 0),
        note: refs.editLessonNote.value.trim(),
        type: refs.editLessonType.value,
        dayId: dayId || null,
        groupId: selectedGroup ? selectedGroup.id : null,
        instructorIds
    };
}

function getCabinetRange() {
    return {
        from: refs.cabinetFrom.value,
        to: refs.cabinetTo.value
    };
}

function populateUserPicker(users) {
    const options = ['<option value="">-- выбери user --</option>']
        .concat(users.map((user) => `<option value="${escapeAttribute(user.id)}">${escapeHtml(user.username)} · ${escapeHtml(user.role)}</option>`));
    refs.userPicker.innerHTML = options.join('');
}

function populateInstructorPicker(users) {
    const instructors = users.filter((user) => user.canTeach);
    const options = instructors.map((user) => `
        <option value="${escapeAttribute(user.id)}">
            ${escapeHtml(user.fullName || user.username)} · ${escapeHtml(user.role)}
        </option>
    `);
    refs.lessonInstructorPicker.innerHTML = options.join('');
}

function populateGroupPicker(groups) {
    const options = ['<option value="">-- выбери группу --</option>']
        .concat(groups.map((group) => `<option value="${escapeAttribute(group.id)}">${escapeHtml(group.code)} · ${escapeHtml(group.location || '-')}</option>`));
    refs.lessonGroupPicker.innerHTML = options.join('');
    syncDayPickerFromSelectedGroup();
}

function syncDayPickerFromSelectedGroup() {
    const group = getSelectedGroup();
    if (!group) {
        refs.lessonDayPicker.innerHTML = '<option value="">-- сначала группа --</option>';
        return;
    }

    const options = ['<option value="">-- выбери день --</option>']
        .concat((group.days || []).map((day) => `<option value="${escapeAttribute(day.id)}">${escapeHtml(day.date)}</option>`));
    refs.lessonDayPicker.innerHTML = options.join('');
}

function getSelectedGroup() {
    return state.groups.find((group) => String(group.id) === refs.lessonGroupPicker.value) || null;
}

function applyUserToForm(user) {
    refs.editUserId.value = user.id || '';
    refs.editUsername.value = user.username || '';
    refs.editUserPassword.value = '';
    refs.editUserFullName.value = user.fullName || '';
    refs.editUserEmail.value = user.email || '';
    refs.editUserPhone.value = user.phone || '';
    refs.editUserPosition.value = user.position || '';
    refs.editUserDepartment.value = user.department || '';
    refs.editUserRole.value = user.role || 'EDITOR';
    refs.editUserActive.checked = user.active !== false;
    refs.editUserCanTeach.checked = user.canTeach !== false;
}

function applyLessonToForm(lesson) {
    refs.editLessonId.value = lesson.id || '';
    refs.editLessonVersion.value = lesson.version ?? '';
    refs.editLessonOrderNumber.value = lesson.orderNumber ?? '';
    refs.editLessonDurationHours.value = lesson.durationHours ?? '';
    refs.editLessonTitle.value = lesson.title || '';
    refs.editLessonType.value = lesson.type || 'LECTURE';
    refs.editLessonNote.value = lesson.note || '';

    if (lesson.groupId) {
        refs.lessonGroupPicker.value = lesson.groupId;
        syncDayPickerFromSelectedGroup();
    }
    if (lesson.dayId) {
        refs.lessonDayPicker.value = lesson.dayId;
    }

    const selectedIds = new Set((lesson.instructorIds || []).map(String));
    Array.from(refs.lessonInstructorPicker.options).forEach((option) => {
        option.selected = selectedIds.has(option.value);
    });
}

async function apiRequest({path, method = 'GET', auth = null, jsonBody = null, formBody = null}) {
    const url = path.startsWith('http') ? path : `${getBaseUrl()}${path}`;
    const headers = new Headers();

    // Basic auth is enough here, we are just poking the gateway.
    if (auth && auth.username) {
        headers.set('Authorization', `Basic ${btoa(`${auth.username}:${auth.password || ''}`)}`);
    }

    let body;
    if (jsonBody) {
        headers.set('Content-Type', 'application/json');
        body = JSON.stringify(jsonBody);
    } else if (formBody) {
        body = formBody;
    }

    const response = await fetch(url, {method, headers, body});
    const raw = await response.text();
    const contentType = response.headers.get('content-type') || '';
    const parsed = parseResponse(raw, contentType);

    if (!response.ok) {
        const message = typeof parsed === 'string' ? parsed : JSON.stringify(parsed, null, 2);
        throw new Error(`${response.status} ${response.statusText}: ${message}`);
    }

    refs.gatewayStatus.textContent = 'ok';
    return parsed;
}

function parseResponse(raw, contentType) {
    if (!raw) {
        return {};
    }

    if (contentType.includes('application/json')) {
        return JSON.parse(raw);
    }

    const trimmed = raw.trim();
    if ((trimmed.startsWith('{') && trimmed.endsWith('}')) || (trimmed.startsWith('[') && trimmed.endsWith(']'))) {
        return JSON.parse(trimmed);
    }

    return raw;
}

function log(message, type = 'info') {
    const entry = document.createElement('div');
    entry.className = `log-entry ${type}`;
    entry.textContent = `[${new Date().toLocaleTimeString('ru-RU')}] ${message}`;
    refs.logHost.prepend(entry);
}

function renderStatus() {
    refs.lastAction.textContent = state.lastAction;
    refs.lastLessonId.textContent = state.lastLessonId || '-';
}

function setLastAction(action) {
    state.lastAction = action;
    renderStatus();
}

function setLastLessonId(lessonId) {
    state.lastLessonId = lessonId || state.lastLessonId;
    renderStatus();
}

function setRawOutput(data) {
    if (typeof data === 'string') {
        refs.rawHost.textContent = truncateText(data, VIEW_LIMITS.rawPreviewChars);
        return;
    }

    const preview = buildRawPreview(data);
    refs.rawHost.textContent = JSON.stringify(preview, null, 2);
}

function renderProfileSummary(profile = null) {
    if (!profile) {
        refs.profileSummary.innerHTML = '<div class="placeholder">Профиль ещё не загружали.</div>';
        return;
    }

    refs.profileSummary.innerHTML = `
        <div class="profile-card">
            <div class="profile-hero">
                <div>
                    <h3 class="profile-name">${escapeHtml(profile.fullName || 'Без имени')}</h3>
                    <p class="profile-subtitle">${escapeHtml(profile.username || '-')}</p>
                </div>
                <span class="role-chip">${escapeHtml(profile.role || '-')}</span>
            </div>
            <div class="profile-grid">
                ${renderMetaCard('Email', profile.email)}
                ${renderMetaCard('Телефон', profile.phone)}
                ${renderMetaCard('Должность', profile.position)}
                ${renderMetaCard('Отдел', profile.department)}
                ${renderMetaCard('Активен', String(profile.active))}
                ${renderMetaCard('Может преподавать', String(profile.canTeach))}
            </div>
        </div>
    `;
}

function renderMetaCard(label, value) {
    return `
        <div class="meta-card">
            <strong>${escapeHtml(label)}</strong>
            <span>${escapeHtml(value || '-')}</span>
        </div>
    `;
}

function renderView(title, data) {
    refs.viewTitle.textContent = title;
    refs.viewHost.innerHTML = renderContentByShape(data);
    setRawOutput(data);
    bindNotificationActions();
}

function renderContentByShape(data) {
    if (data && typeof data === 'object' && Object.prototype.hasOwnProperty.call(data, 'error')) {
        return `<div class="placeholder">${escapeHtml(data.error)}</div>`;
    }

    if (Array.isArray(data) && data.length === 0) {
        return '<div class="placeholder">Ответ пришёл пустым массивом.</div>';
    }

    if (Array.isArray(data) && isScheduleEntries(data)) {
        captureLessonFromEntries(data);
        return renderScheduleEntries(data);
    }

    if (Array.isArray(data) && isNotificationArray(data)) {
        return renderNotifications(data);
    }

    if (Array.isArray(data) && isGroupList(data)) {
        return renderGroupGrid(data);
    }

    if (Array.isArray(data) && isUserList(data)) {
        return renderUsersTable(data);
    }

    if (Array.isArray(data) && data.length > 0 && typeof data[0] === 'object') {
        return renderGenericTable(data);
    }

    if (isProfile(data)) {
        renderProfileSummary(data);
        return renderProfileCard(data);
    }

    if (isDashboard(data)) {
        if (data.profile) {
            renderProfileSummary(data.profile);
        }
        return renderDashboard(data);
    }

    if (isGrid(data)) {
        return renderGrid(data);
    }

    if (isWorkloadCalendar(data)) {
        return renderWorkloadCalendar(data);
    }

    return `
        <div class="placeholder">
            Нет специального рендера под этот ответ. Смотри Raw JSON ниже.
        </div>
    `;
}

function renderProfileCard(profile) {
    return `
        <div class="profile-card">
            <div class="profile-hero">
                <div>
                    <h3 class="profile-name">${escapeHtml(profile.fullName || 'Без имени')}</h3>
                    <p class="profile-subtitle">${escapeHtml(profile.username || '-')}</p>
                </div>
                <span class="role-chip">${escapeHtml(profile.role || '-')}</span>
            </div>
            <div class="profile-grid">
                ${renderMetaCard('Email', profile.email)}
                ${renderMetaCard('Телефон', profile.phone)}
                ${renderMetaCard('Должность', profile.position)}
                ${renderMetaCard('Отдел', profile.department)}
            </div>
        </div>
    `;
}

function renderScheduleEntries(entries) {
    if (!entries.length) {
        return '<div class="placeholder">Расписание пустое.</div>';
    }

    const visibleEntries = entries.slice(0, VIEW_LIMITS.scheduleRows);
    const rows = visibleEntries.map((item) => `
        <tr>
            <td>${escapeHtml(item.date)}</td>
            <td>${escapeHtml(item.groupCode)}</td>
            <td>${escapeHtml(item.orderNumber)}</td>
            <td>${escapeHtml(item.title)}</td>
            <td>${escapeHtml(item.type)}</td>
            <td>${escapeHtml(item.durationHours)}</td>
            <td>${escapeHtml((item.instructorNames || []).join(', '))}</td>
            <td>${escapeHtml(item.note)}</td>
        </tr>
    `).join('');

    return `
        ${renderLimitNote(entries.length, visibleEntries.length, 'занятий')}
        <div class="table-shell">
            <table class="data-table">
                <thead>
                <tr>
                    <th>Дата</th>
                    <th>Группа</th>
                    <th>Пара</th>
                    <th>Предмет</th>
                    <th>Тип</th>
                    <th>Часы</th>
                    <th>Инструкторы</th>
                    <th>Заметка</th>
                </tr>
                </thead>
                <tbody>${rows}</tbody>
            </table>
        </div>
    `;
}

function renderGrid(grid) {
    if (!grid.groups || !grid.groups.length) {
        return '<div class="placeholder">По этому диапазону сетка пустая.</div>';
    }

    const prepared = limitGrid(grid);
    const head = prepared.dates.map((date) => `<th>${escapeHtml(date)}</th>`).join('');
    const body = prepared.groups.map((group) => {
        const dayCells = (group.days || []).map((day) => {
            const lessons = (day.lessons || []).length
                ? `<div class="lesson-stack">${day.lessons.map((lesson) => renderLessonCard(lesson)).join('')}</div>`
                : '<div class="cell-empty">свободно</div>';

            const overflow = day.hiddenLessons > 0
                ? `<div class="cell-more">+ ещё ${escapeHtml(day.hiddenLessons)} занятия</div>`
                : '';

            return `<td><div class="cell-stack">${lessons}${overflow}</div></td>`;
        }).join('');

        return `
            <tr>
                <td class="sticky-col">
                    <div class="grid-group">
                        <strong>${escapeHtml(group.groupCode)}</strong>
                        <span class="grid-meta">Аудитория: ${escapeHtml(group.location)}</span>
                        <span class="grid-meta">Курс: ${escapeHtml(group.course)}</span>
                    </div>
                </td>
                ${dayCells}
            </tr>
        `;
    }).join('');

    return `
        ${renderGridSummary(prepared.meta)}
        <div class="table-shell">
            <table class="schedule-grid">
                <thead>
                <tr>
                    <th class="sticky-col">Группа</th>
                    ${head}
                </tr>
                </thead>
                <tbody>${body}</tbody>
            </table>
        </div>
    `;
}

function renderLessonCard(lesson) {
    if (lesson.lessonId) {
        setLastLessonId(lesson.lessonId);
    }

    return `
        <article class="lesson-card">
            <h3>${escapeHtml(lesson.orderNumber)}. ${escapeHtml(lesson.title)}</h3>
            <p>${escapeHtml(lesson.type)} · ${escapeHtml(lesson.durationHours)} ч.</p>
            <div class="inline-meta">
                <span class="meta-chip">${escapeHtml((lesson.instructorNames || []).join(', ') || 'без инструктора')}</span>
                <span class="meta-chip">v${escapeHtml(lesson.version)}</span>
            </div>
        </article>
    `;
}

function renderWorkloadCalendar(payload) {
    const visibleDays = (payload.days || []).slice(0, VIEW_LIMITS.workloadDays);
    const dayCards = visibleDays.map((day) => `
        <article class="calendar-day">
            <h3>${escapeHtml(day.date)} · ${escapeHtml(day.totalHours)} ч.</h3>
            <div class="lesson-stack">
                ${(day.lessons || []).slice(0, VIEW_LIMITS.lessonsPerCell).map((lesson) => `
                    <div class="lesson-card">
                        <h3>${escapeHtml(lesson.title)}</h3>
                        <p>${escapeHtml(lesson.groupCode)} · ${escapeHtml(lesson.durationHours)} ч.</p>
                    </div>
                `).join('')}
            </div>
        </article>
    `).join('');

    return `
        <div class="stats-grid">
            <div class="stat-card">
                <strong>Инструктор</strong>
                <div class="stat-value">${escapeHtml(payload.instructorName || '-')}</div>
            </div>
            <div class="stat-card">
                <strong>Сумма часов</strong>
                <div class="stat-value">${escapeHtml(payload.totalHours)}</div>
            </div>
            <div class="stat-card">
                <strong>Дни с занятиями</strong>
                <div class="stat-value">${escapeHtml((payload.days || []).length)}</div>
            </div>
        </div>
        ${renderLimitNote((payload.days || []).length, visibleDays.length, 'дней workload')}
        <div class="day-stack">
            ${dayCards || '<div class="placeholder">Занятий за период нет.</div>'}
        </div>
    `;
}

function renderNotifications(items) {
    if (!items.length) {
        return '<div class="placeholder">Уведомлений за период нет.</div>';
    }

    const visibleItems = items.slice(0, VIEW_LIMITS.notifications);
    const cards = visibleItems.map((item) => `
        <article class="notification-card">
            <h3>${escapeHtml(item.date)} · ${escapeHtml(item.type)}</h3>
            <p>${escapeHtml(item.message)}</p>
            <button type="button" data-notification-link="${escapeAttribute(item.link)}">Открыть день</button>
        </article>
    `).join('');

    return `
        ${renderLimitNote(items.length, visibleItems.length, 'уведомлений')}
        <div class="notification-list">${cards}</div>
    `;
}

function renderDashboard(data) {
    const notificationCount = (data.notifications || []).length;
    const dayCount = (data.workload && data.workload.days) ? data.workload.days.length : 0;
    const totalHours = data.workload ? data.workload.totalHours : 0;

    return `
        <div class="stats-grid">
            <div class="stat-card">
                <strong>Инструктор</strong>
                <div class="stat-value">${escapeHtml(data.profile?.fullName || '-')}</div>
            </div>
            <div class="stat-card">
                <strong>Часы за период</strong>
                <div class="stat-value">${escapeHtml(totalHours)}</div>
            </div>
            <div class="stat-card">
                <strong>Дни с занятиями</strong>
                <div class="stat-value">${escapeHtml(dayCount)}</div>
            </div>
            <div class="stat-card">
                <strong>Notifications</strong>
                <div class="stat-value">${escapeHtml(notificationCount)}</div>
            </div>
        </div>
        <div class="profile-card">
            ${renderProfileCard(data.profile || {})}
        </div>
        <div class="panel-head"><h2>Моя сетка</h2></div>
        ${renderGrid(data.instructorSchedule || {dates: [], groups: []})}
        <div class="panel-head"><h2>Уведомления</h2></div>
        ${renderNotifications(data.notifications || [])}
    `;
}

function renderGenericTable(items) {
    const visibleItems = items.slice(0, VIEW_LIMITS.genericRows);
    const keys = Object.keys(visibleItems[0]).slice(0, 8);
    const head = keys.map((key) => `<th>${escapeHtml(key)}</th>`).join('');
    const body = visibleItems.map((item) => `
        <tr>
            ${keys.map((key) => `<td>${escapeHtml(formatValue(item[key]))}</td>`).join('')}
        </tr>
    `).join('');

    return `
        ${renderLimitNote(items.length, visibleItems.length, 'строк')}
        <div class="table-shell">
            <table class="data-table">
                <thead><tr>${head}</tr></thead>
                <tbody>${body}</tbody>
            </table>
        </div>
    `;
}

function renderUsersTable(users) {
    const visibleUsers = users.slice(0, VIEW_LIMITS.genericRows);
    const rows = visibleUsers.map((user) => `
        <tr>
            <td>${escapeHtml(user.username)}</td>
            <td>${escapeHtml(user.fullName)}</td>
            <td>${escapeHtml(user.role)}</td>
            <td>${escapeHtml(user.email)}</td>
            <td>${escapeHtml(user.phone)}</td>
            <td>${escapeHtml(user.position)}</td>
            <td>${escapeHtml(user.department)}</td>
            <td>${escapeHtml(user.active)}</td>
        </tr>
    `).join('');

    return `
        ${renderLimitNote(users.length, visibleUsers.length, 'users')}
        <div class="table-shell">
            <table class="data-table">
                <thead>
                <tr>
                    <th>Username</th>
                    <th>ФИО</th>
                    <th>Role</th>
                    <th>Email</th>
                    <th>Телефон</th>
                    <th>Должность</th>
                    <th>Отдел</th>
                    <th>Active</th>
                </tr>
                </thead>
                <tbody>${rows}</tbody>
            </table>
        </div>
    `;
}

function renderGroupGrid(groups) {
    return renderGrid(groupsToGrid(groups));
}

function groupsToGrid(groups) {
    const dates = Array.from(new Set(groups
        .flatMap((group) => (group.days || []).map((day) => day.date))))
        .sort();

    const rows = groups.map((group) => {
        const dayMap = new Map((group.days || []).map((day) => [day.date, day]));
        return {
            groupId: group.id,
            groupCode: group.code,
            location: group.location,
            course: group.course,
            days: dates.map((date) => {
                const day = dayMap.get(date);
                return {
                    dayId: day?.id || null,
                    date,
                    lessons: normalizeLessonsForGrid(day?.lessons || [])
                };
            })
        };
    });

    return {dates, groups: rows};
}

function normalizeLessonsForGrid(lessons) {
    return lessons.map((lesson) => ({
        lessonId: lesson.lessonId || lesson.id || null,
        version: lesson.version,
        orderNumber: lesson.orderNumber,
        title: lesson.title,
        type: lesson.type,
        durationHours: lesson.durationHours,
        note: lesson.note,
        instructorNames: lesson.instructorNames?.length
            ? lesson.instructorNames
            : lesson.lecturers?.length
                ? lesson.lecturers
                : lesson.lecturer
                    ? [lesson.lecturer]
                    : []
    }));
}

function limitGrid(grid) {
    const allDates = grid.dates || [];
    const allGroups = grid.groups || [];
    const visibleDates = allDates.slice(0, VIEW_LIMITS.gridDates);
    const visibleGroups = allGroups.slice(0, VIEW_LIMITS.gridGroups).map((group) => {
        const days = (group.days || []).slice(0, visibleDates.length).map((day) => ({
            ...day,
            lessons: (day.lessons || []).slice(0, VIEW_LIMITS.lessonsPerCell),
            hiddenLessons: Math.max(0, (day.lessons || []).length - VIEW_LIMITS.lessonsPerCell)
        }));

        return {...group, days};
    });

    const totalLessons = allGroups.reduce((sum, group) => sum + (group.days || []).reduce((daySum, day) => daySum + (day.lessons || []).length, 0), 0);

    return {
        dates: visibleDates,
        groups: visibleGroups,
        meta: {
            totalGroups: allGroups.length,
            shownGroups: visibleGroups.length,
            totalDates: allDates.length,
            shownDates: visibleDates.length,
            totalLessons
        }
    };
}

function renderGridSummary(meta) {
    const notes = [];
    if (meta.totalGroups > meta.shownGroups) {
        notes.push(`показано ${meta.shownGroups} из ${meta.totalGroups} групп`);
    }
    if (meta.totalDates > meta.shownDates) {
        notes.push(`показано ${meta.shownDates} из ${meta.totalDates} дат`);
    }
    notes.push(`всего занятий: ${meta.totalLessons}`);

    return `
        <div class="summary-strip">
            ${notes.map((note) => `<span class="summary-chip">${escapeHtml(note)}</span>`).join('')}
        </div>
    `;
}

function renderLimitNote(total, shown, label) {
    if (total <= shown) {
        return '';
    }

    return `
        <div class="limit-note">
            Показано ${escapeHtml(shown)} из ${escapeHtml(total)} ${escapeHtml(label)}. Остальное урезано, чтобы UI не подвисал.
        </div>
    `;
}

function buildRawPreview(data) {
    return createPreviewValue(data, 0);
}

function createPreviewValue(value, depth) {
    if (value == null) {
        return value;
    }

    if (typeof value === 'string') {
        return truncateText(value, VIEW_LIMITS.stringPreviewChars);
    }

    if (typeof value !== 'object') {
        return value;
    }

    if (Array.isArray(value)) {
        const maxItems = depth === 0 ? VIEW_LIMITS.genericRows : 6;
        const visibleItems = value.slice(0, maxItems).map((item) => createPreviewValue(item, depth + 1));

        if (value.length > visibleItems.length) {
            visibleItems.push(`[+${value.length - visibleItems.length} items hidden]`);
        }

        return visibleItems;
    }

    const entries = Object.entries(value);
    const maxKeys = depth === 0 ? 20 : 10;
    const result = {};
    for (const [key, item] of entries.slice(0, maxKeys)) {
        result[key] = createPreviewValue(item, depth + 1);
    }
    if (entries.length > maxKeys) {
        result._truncatedKeys = entries.length - maxKeys;
    }
    return result;
}

function bindNotificationActions() {
    refs.viewHost.querySelectorAll('[data-notification-link]').forEach((button) => {
        button.addEventListener('click', async () => {
            const link = button.getAttribute('data-notification-link');
            await loadNotificationDay(link);
        });
    });
}

async function runRequest(actionName, requestFactory, afterSuccess = null) {
    try {
        const data = await requestFactory();
        setLastAction(actionName);
        renderView(actionName, data);
        log(`${actionName}: ok`, 'success');
        if (afterSuccess) {
            await afterSuccess(data);
        }
    } catch (error) {
        refs.gatewayStatus.textContent = 'ошибка';
        setLastAction(`${actionName} (ошибка)`);
        renderView(actionName, {error: error.message});
        log(`${actionName}: ${error.message}`, 'error');
    }
}

async function refreshUsersQuiet() {
    const data = await apiRequest({
        path: '/api/users',
        auth: getAdminAuth()
    });
    state.users = Array.isArray(data) ? data : [];
    populateUserPicker(state.users);
    populateInstructorPicker(state.users);
    return state.users;
}

async function refreshGroupsQuiet() {
    const data = await apiRequest({
        path: '/api/groups',
        auth: getAdminAuth()
    });
    state.groups = Array.isArray(data) ? data : [];
    populateGroupPicker(state.groups);
    return state.groups;
}

async function importCsv() {
    const file = refs.csvFile.files[0];
    if (!file) {
        log('Сначала выбери CSV файл.', 'error');
        return;
    }

    const form = new FormData();
    form.append('file', file);

    await runRequest('Импорт CSV', () => apiRequest({
        path: '/api/import/csv',
        method: 'POST',
        auth: getAdminAuth(),
        formBody: form
    }));
}

async function loadAdminMe() {
    await runRequest('Admin /api/auth/me', () => apiRequest({
        path: '/api/auth/me',
        auth: getAdminAuth()
    }));
}

async function loadUsers() {
    await runRequest('Список users', () => apiRequest({
        path: '/api/users',
        auth: getAdminAuth()
    }), (data) => {
        state.users = Array.isArray(data) ? data : [];
        populateUserPicker(state.users);
        populateInstructorPicker(state.users);
    });
}

async function loadGroups() {
    await runRequest('Список групп', () => apiRequest({
        path: '/api/groups',
        auth: getAdminAuth()
    }), (data) => {
        state.groups = Array.isArray(data) ? data : [];
        populateGroupPicker(state.groups);
    });
}

async function loadLegacyWorkload() {
    await runRequest('Legacy workload', () => apiRequest({
        path: '/api/workload',
        auth: getAdminAuth()
    }));
}

async function loadLessonById() {
    if (!state.lastLessonId) {
        log('lessonId ещё не поймали. Сначала открой расписание или сетку.', 'error');
        return;
    }

    if (!state.groups.length) {
        await refreshGroupsQuiet();
    }
    if (!state.users.length) {
        await refreshUsersQuiet();
    }

    await runRequest('Lesson by id', () => apiRequest({
        path: `/api/lessons/${state.lastLessonId}`,
        auth: getAdminAuth()
    }), (data) => {
        applyLessonToForm(data);
    });
}

async function loadLessonHistory() {
    if (!state.lastLessonId) {
        log('lessonId ещё не поймали. Сначала открой расписание или сетку.', 'error');
        return;
    }

    await runRequest('История lesson', () => apiRequest({
        path: `/api/lessons/${state.lastLessonId}/history`,
        auth: getAdminAuth()
    }));
}

function fillUserFormFromPicker() {
    const user = state.users.find((item) => String(item.id) === refs.userPicker.value);
    if (!user) {
        log('Сначала выбери user в селекте или загрузи список users.', 'error');
        return;
    }
    applyUserToForm(user);
    log(`User ${user.username} подставлен в форму. Пароль заполни руками.`, 'info');
}

async function createUser() {
    const payload = readUserPayload();
    if (!payload.username || !payload.password || !payload.fullName) {
        log('Для создания user нужны username, password и fullName.', 'error');
        return;
    }

    await runRequest('Создать user', () => apiRequest({
        path: '/api/users',
        method: 'POST',
        auth: getAdminAuth(),
        jsonBody: payload
    }), async (data) => {
        applyUserToForm(data);
        await refreshUsersQuiet();
    });
}

async function updateUser() {
    const userId = refs.editUserId.value.trim();
    const payload = readUserPayload();

    if (!userId) {
        log('Для update user нужен userId. Выбери user из списка.', 'error');
        return;
    }

    if (!payload.password) {
        log('Для update user тут нужен password тоже. Это контракт backend.', 'error');
        return;
    }

    await runRequest('Обновить user', () => apiRequest({
        path: `/api/users/${userId}`,
        method: 'PUT',
        auth: getAdminAuth(),
        jsonBody: payload
    }), async (data) => {
        applyUserToForm(data);
        await refreshUsersQuiet();
    });
}

async function loadLessonIntoForm() {
    if (!state.lastLessonId) {
        log('lastLessonId пустой. Сначала открой расписание, сетку или lesson by id.', 'error');
        return;
    }

    if (!state.groups.length) {
        await refreshGroupsQuiet();
    }
    if (!state.users.length) {
        await refreshUsersQuiet();
    }

    await runRequest('Lesson в форму', () => apiRequest({
        path: `/api/lessons/${state.lastLessonId}`,
        auth: getAdminAuth()
    }), (data) => {
        applyLessonToForm(data);
    });
}

async function createLesson() {
    const payload = readLessonPayload();
    if (!payload.dayId || !payload.title) {
        log('Для создания lesson нужны минимум dayId и title.', 'error');
        return;
    }

    await runRequest('Создать lesson', () => apiRequest({
        path: '/api/lessons',
        method: 'POST',
        auth: getAdminAuth(),
        jsonBody: payload
    }), (data) => {
        applyLessonToForm(data);
        setLastLessonId(data.id);
    });
}

async function updateLesson() {
    const payload = readLessonPayload();
    if (!payload.id) {
        log('Для update lesson нужен lessonId. Загрузи lesson в форму.', 'error');
        return;
    }
    if (payload.version == null) {
        log('Для update lesson нужна version. Без неё optimistic locking вернёт ошибку.', 'error');
        return;
    }

    await runRequest('Обновить lesson', () => apiRequest({
        path: `/api/lessons/${payload.id}`,
        method: 'PUT',
        auth: getAdminAuth(),
        jsonBody: payload
    }), (data) => {
        applyLessonToForm(data);
        setLastLessonId(data.id);
    });
}

async function loadPublicScheduleByGroup() {
    const params = new URLSearchParams({groupCode: refs.groupCode.value.trim()});
    await runRequest('Публичное расписание группы', async () => {
        const data = await apiRequest({path: `/api/public/schedule?${params.toString()}`});
        captureLessonFromEntries(data);
        return data;
    });
}

async function loadPublicScheduleByDay() {
    const params = new URLSearchParams({
        groupCode: refs.groupCode.value.trim(),
        from: refs.publicFrom.value,
        to: refs.publicTo.value
    });
    await runRequest('Публичное расписание дня', async () => {
        const data = await apiRequest({path: `/api/public/schedule?${params.toString()}`});
        captureLessonFromEntries(data);
        return data;
    });
}

async function loadMyProfile() {
    await runRequest('Мой профиль', () => apiRequest({
        path: '/api/me/profile',
        auth: getInstructorAuth()
    }), (data) => {
        applyProfileToForm(data);
        renderProfileSummary(data);
    });
}

async function saveMyProfile() {
    const payload = readProfilePayload();
    if (!payload.fullName) {
        log('ФИО пустое. Для профиля это обязательно.', 'error');
        return;
    }

    await runRequest('Сохранить профиль', () => apiRequest({
        path: '/api/me/profile',
        method: 'PUT',
        auth: getInstructorAuth(),
        jsonBody: payload
    }), (data) => {
        renderProfileSummary(data);
    });
}

async function changeMyPassword() {
    const currentPassword = refs.currentPassword.value;
    const newPassword = refs.newPassword.value;

    if (!currentPassword || !newPassword) {
        log('Заполни текущий и новый пароль.', 'error');
        return;
    }

    await runRequest('Смена пароля', () => apiRequest({
        path: '/api/me/password',
        method: 'PUT',
        auth: getInstructorAuth(),
        jsonBody: {currentPassword, newPassword}
    }), () => {
        refs.instructorPass.value = newPassword;
        refs.currentPassword.value = '';
        refs.newPassword.value = '';
        log('Пароль в форме тоже обновил, чтобы следующие запросы не упали.', 'info');
    });
}

async function loadDashboard() {
    const params = new URLSearchParams(getCabinetRange());
    await runRequest('Dashboard', () => apiRequest({
        path: `/api/me/dashboard?${params.toString()}`,
        auth: getInstructorAuth()
    }), (data) => {
        if (data.profile) {
            applyProfileToForm(data.profile);
            renderProfileSummary(data.profile);
        }
    });
}

async function loadFullGrid() {
    const params = new URLSearchParams(getCabinetRange());
    await runRequest('Полная сетка', async () => {
        const data = await apiRequest({
            path: `/api/me/schedule/grid?${params.toString()}`,
            auth: getInstructorAuth()
        });
        captureLessonFromGrid(data);
        return data;
    });
}

async function loadInstructorGrid() {
    const params = new URLSearchParams(getCabinetRange());
    await runRequest('Моя сетка', async () => {
        const data = await apiRequest({
            path: `/api/me/schedule/instructor-grid?${params.toString()}`,
            auth: getInstructorAuth()
        });
        captureLessonFromGrid(data);
        return data;
    });
}

async function loadWorkloadCalendar() {
    const params = new URLSearchParams(getCabinetRange());
    await runRequest('Workload calendar', () => apiRequest({
        path: `/api/me/workload/calendar?${params.toString()}`,
        auth: getInstructorAuth()
    }));
}

async function loadNotifications() {
    const params = new URLSearchParams(getCabinetRange());
    await runRequest('Notifications', () => apiRequest({
        path: `/api/me/notifications?${params.toString()}`,
        auth: getInstructorAuth()
    }));
}

async function loadNotificationDay(link) {
    // Notification link already contains from/to, just reuse it.
    await runRequest(`Notifications -> ${link}`, async () => {
        const data = await apiRequest({
            path: link,
            auth: getInstructorAuth()
        });
        captureLessonFromGrid(data);
        return data;
    });
}

function captureLessonFromEntries(entries) {
    if (Array.isArray(entries) && entries[0] && entries[0].lessonId) {
        setLastLessonId(entries[0].lessonId);
    }
}

function captureLessonFromGrid(grid) {
    if (!isGrid(grid)) {
        return;
    }

    for (const group of grid.groups || []) {
        for (const day of group.days || []) {
            for (const lesson of day.lessons || []) {
                if (lesson.lessonId) {
                    setLastLessonId(lesson.lessonId);
                    return;
                }
            }
        }
    }
}

function applyProfileToForm(profile) {
    refs.profileFullName.value = profile.fullName || '';
    refs.profileEmail.value = profile.email || '';
    refs.profilePhone.value = profile.phone || '';
    refs.profilePosition.value = profile.position || '';
    refs.profileDepartment.value = profile.department || '';
}

function isScheduleEntries(data) {
    return Array.isArray(data) && (!data.length || Object.prototype.hasOwnProperty.call(data[0], 'lessonId'));
}

function isGroupList(data) {
    return Array.isArray(data) && data.length > 0
        && Object.prototype.hasOwnProperty.call(data[0], 'code')
        && Array.isArray(data[0].days);
}

function isUserList(data) {
    return Array.isArray(data) && data.length > 0
        && Object.prototype.hasOwnProperty.call(data[0], 'username')
        && Object.prototype.hasOwnProperty.call(data[0], 'role');
}

function isNotificationArray(data) {
    return Array.isArray(data) && data.length > 0 && Object.prototype.hasOwnProperty.call(data[0], 'link');
}

function isProfile(data) {
    return data && typeof data === 'object' && Object.prototype.hasOwnProperty.call(data, 'username') && Object.prototype.hasOwnProperty.call(data, 'role');
}

function isGrid(data) {
    return data && typeof data === 'object' && Array.isArray(data.dates) && Array.isArray(data.groups);
}

function isWorkloadCalendar(data) {
    return data && typeof data === 'object' && Object.prototype.hasOwnProperty.call(data, 'totalHours') && Array.isArray(data.days);
}

function isDashboard(data) {
    return data && typeof data === 'object'
        && Object.prototype.hasOwnProperty.call(data, 'profile')
        && Object.prototype.hasOwnProperty.call(data, 'instructorSchedule')
        && Object.prototype.hasOwnProperty.call(data, 'workload')
        && Object.prototype.hasOwnProperty.call(data, 'notifications');
}

function formatValue(value) {
    if (value == null) {
        return '-';
    }

    if (Array.isArray(value)) {
        return truncateText(value.map((item) => formatValue(item)).join(', '), VIEW_LIMITS.stringPreviewChars);
    }

    if (typeof value === 'object') {
        return truncateText(JSON.stringify(createPreviewValue(value, 1)), VIEW_LIMITS.stringPreviewChars);
    }

    return truncateText(String(value), VIEW_LIMITS.stringPreviewChars);
}

function escapeHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

function escapeAttribute(value) {
    return escapeHtml(value).replaceAll('`', '&#96;');
}

function truncateText(value, maxLength) {
    const text = String(value ?? '');
    if (text.length <= maxLength) {
        return text;
    }
    return `${text.slice(0, maxLength)}...`;
}
