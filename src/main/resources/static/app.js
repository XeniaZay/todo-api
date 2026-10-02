// ============================================================
// TODO-API — фронтенд
// Все запросы идут на тот же origin (Spring Boot отдаёт этот файл)
// ============================================================

const API = '/api/tasks';

// ---- Состояние ----
const state = {
    page: 0,
    size: 10,
    sortBy: 'id',
    sortDir: 'asc',
    filters: {
        status: '',
        priority: '',
        tag: '',
        search: '',
        overdue: false,
    },
    totalPages: 0,
    lastLoad: null,
};

// ============================================================
// Утилиты
// ============================================================

function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function showToast(message, type = 'success') {
    const el = document.createElement('div');
    el.className = `toast ${type}`;
    el.textContent = message;
    document.body.appendChild(el);
    setTimeout(() => el.remove(), 3000);
}

function formatDate(iso) {
    if (!iso) return '—';
    return new Date(iso).toLocaleDateString('ru-RU');
}

function isOverdue(task) {
    if (!task.due_date) return false;
    if (task.status === 'DONE' || task.status === 'CANCELLED') return false;
    return new Date(task.due_date) < new Date(new Date().toDateString());
}

// ============================================================
// Загрузка списка задач
// ============================================================

async function loadTasks() {
    showLoading(true);

    const params = new URLSearchParams();
    params.append('page', state.page);
    params.append('size', state.size);
    params.append('sortBy', state.sortBy);
    params.append('sortDir', state.sortDir);

    if (state.filters.status)   params.append('status', state.filters.status);
    if (state.filters.priority) params.append('priority', state.filters.priority);
    if (state.filters.tag)      params.append('tag', state.filters.tag);
    if (state.filters.search)   params.append('search', state.filters.search);
    if (state.filters.overdue)  params.append('overdue', 'true');

    try {
        const response = await fetch(`${API}?${params}`);
        if (!response.ok) {
            const err = await response.json();
            throw new Error(err.detail || err.message || 'Ошибка загрузки');
        }

        const data = await response.json();
        state.totalPages = data.total_pages;
        renderTasks(data.content);
        renderPagination(data);
    } catch (err) {
        showToast(err.message, 'error');
    } finally {
        showLoading(false);
    }
}

// ============================================================
// Рендер списка
// ============================================================

function renderTasks(tasks) {
    const container = document.getElementById('task-list');
    const empty = document.getElementById('empty');

    if (!tasks || tasks.length === 0) {
        container.innerHTML = '';
        empty.style.display = 'block';
        return;
    }

    empty.style.display = 'none';
    container.innerHTML = tasks.map(renderTaskCard).join('');
}

function renderTaskCard(task) {
    const overdue = isOverdue(task);
    const cardClass = [
        'task-card',
        task.status === 'DONE' ? 'done' : '',
        overdue ? 'overdue' : '',
    ].filter(Boolean).join(' ');

    const tagsHtml = (task.tags || []).length
        ? `<div class="task-tags">${task.tags.map(t => `<span class="tag">${escapeHtml(t)}</span>`).join('')}</div>`
        : '';

    const overdueBadge = overdue
        ? `<span class="badge badge-URGENT">Просрочено</span>`
        : '';

    return `
        <div class="${cardClass}" data-id="${task.id}">
            <div class="task-header">
                <h3 class="task-title">${escapeHtml(task.title)}</h3>
                <div class="task-actions">
                    <select class="status-select" data-id="${task.id}">
                        ${['TODO', 'IN_PROGRESS', 'DONE', 'CANCELLED'].map(s =>
                            `<option value="${s}" ${task.status === s ? 'selected' : ''}>${s}</option>`
                        ).join('')}
                    </select>
                    <button class="delete-btn outline secondary" data-id="${task.id}" title="Удалить">🗑</button>
                </div>
            </div>

            ${task.description ? `<p style="margin: 0.25rem 0; font-size: 0.9rem;">${escapeHtml(task.description)}</p>` : ''}

            <div class="task-meta">
                <span>ID: ${task.id}</span>
                <span class="badge badge-${task.status}">${task.status}</span>
                <span class="badge badge-${task.priority}">${task.priority}</span>
                <span>📅 ${formatDate(task.due_date)}</span>
                ${overdueBadge}
            </div>

            ${tagsHtml}
        </div>
    `;
}

// ============================================================
// Пагинация
// ============================================================

function renderPagination(data) {
    const pagination = document.getElementById('pagination');
    const pageInfo = document.getElementById('page-info');

    if (data.total_pages <= 1) {
        pagination.style.display = 'none';
        return;
    }

    pagination.style.display = 'flex';
    pageInfo.textContent = `Страница ${data.page + 1} из ${data.total_pages} (всего: ${data.total})`;

    document.getElementById('prev-btn').disabled = data.page === 0;
    document.getElementById('next-btn').disabled = data.page >= data.total_pages - 1;
}

// ============================================================
// Создание задачи
// ============================================================

async function createTask(event) {
    event.preventDefault();

    const title = document.getElementById('f-title').value.trim();
    const description = document.getElementById('f-description').value.trim();
    const priority = document.getElementById('f-priority').value;
    const dueDate = document.getElementById('f-due-date').value;
    const tagsRaw = document.getElementById('f-tags').value.trim();

    // ВАЖНО: бэкенд с SNAKE_CASE ждёт snake_case
    const body = {
        title,
        priority,
    };

    if (description) body.description = description;
    if (dueDate) body.due_date = dueDate;                    // ⚠️ snake_case
    if (tagsRaw) {
        body.tags = tagsRaw.split(',').map(t => t.trim()).filter(Boolean);
    }

    try {
        const response = await fetch(API, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
        });

        if (!response.ok) {
            const err = await response.json();
            const message = extractErrorMessage(err);
            throw new Error(message);
        }

        showToast('Задача создана ✅', 'success');
        document.getElementById('create-form').reset();
        state.page = 0;
        await loadTasks();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// ============================================================
// Смена статуса
// ============================================================

async function changeStatus(id, newStatus) {
    try {
        const response = await fetch(`${API}/${id}/status`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: newStatus }),
        });

        if (!response.ok) {
            const err = await response.json();
            throw new Error(extractErrorMessage(err));
        }

        showToast(`Статус изменён на ${newStatus}`, 'success');
        await loadTasks();
    } catch (err) {
        showToast(err.message, 'error');
        await loadTasks();       // откатить UI до реального состояния
    }
}

// ============================================================
// Удаление
// ============================================================

async function deleteTask(id) {
    if (!confirm('Удалить задачу?')) return;

    try {
        const response = await fetch(`${API}/${id}`, { method: 'DELETE' });
        if (!response.ok) {
            const err = await response.json();
            throw new Error(extractErrorMessage(err));
        }

        showToast('Задача удалена 🗑', 'success');
        await loadTasks();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// ============================================================
// Обработка ошибок
// ============================================================

function extractErrorMessage(err) {
    if (err.violations && err.violations.length) {
        return err.violations.map(v => `${v.field}: ${v.message}`).join('; ');
    }
    return err.detail || err.message || err.title || 'Ошибка';
}

// ============================================================
// UI-хелперы
// ============================================================

function showLoading(show) {
    document.getElementById('loading').style.display = show ? 'block' : 'none';
}

function readFilters() {
    return {
        status: document.getElementById('f-filter-status').value,
        priority: document.getElementById('f-filter-priority').value,
        tag: document.getElementById('f-filter-tag').value.trim(),
        search: document.getElementById('f-filter-search').value.trim(),
        overdue: document.getElementById('f-filter-overdue').checked,
    };
}

function resetFilters() {
    document.getElementById('f-filter-status').value = '';
    document.getElementById('f-filter-priority').value = '';
    document.getElementById('f-filter-tag').value = '';
    document.getElementById('f-filter-search').value = '';
    document.getElementById('f-filter-overdue').checked = false;
    state.filters = { status: '', priority: '', tag: '', search: '', overdue: false };
    state.page = 0;
}

// ============================================================
// Инициализация и привязка событий
// ============================================================

document.addEventListener('DOMContentLoaded', () => {

    // Форма создания
    document.getElementById('create-form').addEventListener('submit', createTask);

    // Фильтры
    document.getElementById('apply-filters-btn').addEventListener('click', async () => {
        state.filters = readFilters();
        state.page = 0;
        await loadTasks();
    });

    document.getElementById('reset-filters-btn').addEventListener('click', async () => {
        resetFilters();
        await loadTasks();
    });

    // Обновить
    document.getElementById('refresh-btn').addEventListener('click', loadTasks);

    // Пагинация
    document.getElementById('prev-btn').addEventListener('click', async () => {
        if (state.page > 0) {
            state.page--;
            await loadTasks();
        }
    });
    document.getElementById('next-btn').addEventListener('click', async () => {
        if (state.page < state.totalPages - 1) {
            state.page++;
            await loadTasks();
        }
    });

    // Делегирование событий для динамических элементов
    document.getElementById('task-list').addEventListener('change', (e) => {
        if (e.target.classList.contains('status-select')) {
            const id = e.target.dataset.id;
            const newStatus = e.target.value;
            changeStatus(id, newStatus);
        }
    });

    document.getElementById('task-list').addEventListener('click', (e) => {
        if (e.target.classList.contains('delete-btn')) {
            const id = e.target.dataset.id;
            deleteTask(id);
        }
    });

    // Первая загрузка
    loadTasks();
});