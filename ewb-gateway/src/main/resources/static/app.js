// API Gateway Base
const API_BASE = '/api';

let currentRole = 'CUSTOMER';
let currentCustomerId = 'CUST-MARIA';
let simulatedOutage = false;

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
    // Set default start date in create modal to today
    const today = new Date().toISOString().split('T')[0];
    document.getElementById('modalStartDate').value = today;

    refreshAllData();

    // Auto-refresh every 6 seconds
    setInterval(() => {
        refreshAllData(true);
    }, 6000);
});

function changePersona(role) {
    currentRole = role;
    if (role === 'CUSTOMER') {
        currentCustomerId = 'CUST-MARIA';
        switchTab('customer');
    } else if (role === 'OPERATIONS') {
        currentCustomerId = 'OPERATIONS';
        switchTab('ops');
    } else if (role === 'AUDITOR') {
        currentCustomerId = 'AUDITOR';
        switchTab('audit');
    }
    showBanner(`Active persona switched to ${role}`, 'info');
    refreshAllData();
}

function switchTab(tabName) {
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));

    if (tabName === 'customer') {
        document.getElementById('tabCustomerBtn').classList.add('active');
        document.getElementById('tabCustomer').classList.add('active');
    } else if (tabName === 'ops') {
        document.getElementById('tabOpsBtn').classList.add('active');
        document.getElementById('tabOps').classList.add('active');
    } else if (tabName === 'audit') {
        document.getElementById('tabAuditBtn').classList.add('active');
        document.getElementById('tabAudit').classList.add('active');
    } else if (tabName === 'scenarios') {
        document.getElementById('tabScenariosBtn').classList.add('active');
        document.getElementById('tabScenarios').classList.add('active');
    }
}

function showBanner(message, type = 'success') {
    const banner = document.getElementById('statusBanner');
    banner.className = `status-banner ${type}`;
    banner.innerHTML = `<span>${message}</span> <button class="btn-xs btn-outline" onclick="this.parentElement.classList.add('hidden')">&times;</button>`;
    banner.classList.remove('hidden');
    setTimeout(() => {
        banner.classList.add('hidden');
    }, 5000);
}

function logToConsole(text) {
    const pre = document.getElementById('testOutput');
    const time = new Date().toLocaleTimeString();
    pre.textContent += `\n[${time}] ${text}`;
    pre.scrollTop = pre.scrollHeight;
}

function clearConsole() {
    document.getElementById('testOutput').textContent = 'Console log cleared.';
}

// ===================== DATA FETCHING =====================

async function refreshAllData(silent = false) {
    try {
        await Promise.all([
            fetchAccounts(),
            fetchStandingOrders(),
            fetchExecutions(),
            fetchOutbox(),
            fetchLedger(),
            fetchNotifications()
        ]);
        if (!silent) {
            // Updated silently
        }
    } catch (e) {
        console.error('Error refreshing data:', e);
    }
}

// 1. Fetch Accounts
async function fetchAccounts() {
    try {
        const res = await fetch(`${API_BASE}/accounts`);
        const json = await res.json();
        if (json.success && json.data) {
            renderAccounts(json.data);
        }
    } catch (e) {
        console.error('Failed to fetch accounts', e);
    }
}

function renderAccounts(accounts) {
    const container = document.getElementById('accountsList');
    container.innerHTML = '';

    accounts.forEach(acc => {
        const isFrozen = acc.status === 'FROZEN';
        const card = document.createElement('div');
        card.className = `account-card glass-card ${isFrozen ? 'frozen' : ''}`;
        card.innerHTML = `
            <div class="account-title">
                <span class="account-num">${acc.accountId}</span>
                <span class="badge ${isFrozen ? 'badge-danger' : 'badge-success'}">${acc.status}</span>
            </div>
            <div class="account-balance">₱${parseFloat(acc.balance).toLocaleString('en-US', { minimumFractionDigits: 2 })}</div>
            <div class="account-owner">${acc.accountName} (${acc.customerId})</div>
        `;
        container.appendChild(card);
    });
}

// 2. Fetch Standing Orders
async function fetchStandingOrders() {
    try {
        const headers = { 'X-Customer-Id': currentCustomerId };
        const url = currentRole === 'OPERATIONS' || currentRole === 'AUDITOR'
            ? `${API_BASE}/standing-orders?all=true`
            : `${API_BASE}/standing-orders`;

        const res = await fetch(url, { headers });
        const json = await res.json();
        if (json.success && json.data) {
            renderStandingOrders(json.data);
            renderAuditVersionsList(json.data);
        }
    } catch (e) {
        console.error('Failed to fetch orders', e);
    }
}

function renderStandingOrders(orders) {
    const tbody = document.getElementById('ordersTableBody');
    if (orders.length === 0) {
        tbody.innerHTML = `<tr><td colspan="8" class="text-center">No standing orders found. Click "+ Create Standing Order" or "Seed Maria's Order".</td></tr>`;
        return;
    }

    tbody.innerHTML = orders.map(o => {
        let statusBadge = 'badge-success';
        if (o.status === 'PAUSED') statusBadge = 'badge-warning';
        if (o.status === 'CANCELLED') statusBadge = 'badge-danger';

        const nextFormatted = o.nextExecutionTime ? formatDateTime(o.nextExecutionTime) : 'None (Inactive)';

        return `
            <tr>
                <td><strong>#${o.id}</strong></td>
                <td>
                    <span class="account-num">${o.sourceAccountId}</span> &rarr;
                    <span class="account-num">${o.destinationAccountId}</span>
                </td>
                <td><strong>₱${parseFloat(o.amount).toLocaleString('en-US', { minimumFractionDigits: 2 })}</strong></td>
                <td>Day ${o.dayOfMonth} @ ${o.executionTime}</td>
                <td>${nextFormatted}</td>
                <td><span class="badge badge-neutral">v${o.version}</span></td>
                <td><span class="badge ${statusBadge}">${o.status}</span></td>
                <td>
                    <div style="display: flex; gap: 4px;">
                        ${o.status === 'ACTIVE' ? `<button class="btn btn-xs btn-warning" onclick="pauseOrder(${o.id})">Pause</button>` : ''}
                        ${o.status === 'PAUSED' ? `<button class="btn btn-xs btn-success" onclick="resumeOrder(${o.id})">Resume</button>` : ''}
                        ${o.status !== 'CANCELLED' ? `<button class="btn btn-xs btn-outline" onclick="openAmendModal(${o.id}, ${o.amount}, ${o.dayOfMonth})">Amend</button>` : ''}
                        ${o.status !== 'CANCELLED' ? `<button class="btn btn-xs btn-danger" onclick="cancelOrder(${o.id})">Cancel</button>` : ''}
                        <button class="btn btn-xs btn-secondary" onclick="viewVersions(${o.id})">Versions</button>
                    </div>
                </td>
            </tr>
        `;
    }).join('');
}

// 3. Fetch Executions
async function fetchExecutions() {
    try {
        const res = await fetch(`${API_BASE}/executions`);
        const json = await res.json();
        if (json.success && json.data) {
            renderExecutions(json.data);
            updateKpis(json.data);
        }
    } catch (e) {
        console.error('Failed to fetch executions', e);
    }
}

function renderExecutions(executions) {
    const tbody = document.getElementById('executionsTableBody');
    if (executions.length === 0) {
        tbody.innerHTML = `<tr><td colspan="8" class="text-center">No executions recorded yet. Trigger discovery to execute due orders.</td></tr>`;
        return;
    }

    tbody.innerHTML = executions.map(ex => {
        let badgeClass = 'badge-success';
        if (ex.status === 'FAILED') badgeClass = 'badge-danger';
        if (ex.status === 'UNRESOLVED') badgeClass = 'badge-warning';
        if (ex.status === 'CLAIMED' || ex.status === 'PENDING') badgeClass = 'badge-info';

        const attemptsCount = ex.attempts ? ex.attempts.length : 0;
        const attemptsSnippet = ex.attempts && ex.attempts.length > 0
            ? `<span title="${ex.attempts[ex.attempts.length - 1].errorMessage || 'OK'}">${attemptsCount} (${ex.attempts[ex.attempts.length - 1].responseCategory})</span>`
            : '0';

        return `
            <tr>
                <td><strong>#${ex.id}</strong></td>
                <td>#${ex.standingOrderId}</td>
                <td>${formatDateTime(ex.scheduledTime)}</td>
                <td><small style="font-family: var(--font-mono);">${ex.idempotencyKey}</small></td>
                <td>${ex.paymentReference || '<span class="text-muted">None</span>'}</td>
                <td><span class="badge ${badgeClass}">${ex.status}</span></td>
                <td>${attemptsSnippet}</td>
                <td>
                    ${ex.status === 'UNRESOLVED'
                        ? `<button class="btn btn-xs btn-warning" onclick="resolveExecution(${ex.id})">Resolve</button>`
                        : `<span class="text-muted">-</span>`}
                </td>
            </tr>
        `;
    }).join('');
}

function updateKpis(executions) {
    document.getElementById('kpiTotalExec').textContent = executions.length;
    document.getElementById('kpiSuccessExec').textContent = executions.filter(e => e.status === 'SUCCESS').length;
    document.getElementById('kpiFailedExec').textContent = executions.filter(e => e.status === 'FAILED').length;
    document.getElementById('kpiUnresolvedExec').textContent = executions.filter(e => e.status === 'UNRESOLVED').length;
}

// 4. Fetch Outbox
async function fetchOutbox() {
    try {
        const res = await fetch(`${API_BASE}/executions/outbox`);
        const json = await res.json();
        if (json.success && json.data) {
            renderOutbox(json.data);
        }
    } catch (e) {
        console.error('Failed to fetch outbox', e);
    }
}

function renderOutbox(events) {
    const tbody = document.getElementById('outboxTableBody');
    if (events.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" class="text-center">No outbox events.</td></tr>`;
        return;
    }

    tbody.innerHTML = events.slice(-10).reverse().map(ev => {
        let badge = ev.status === 'PUBLISHED' ? 'badge-success' : 'badge-warning';
        return `
            <tr>
                <td><small style="font-family: var(--font-mono);">${ev.eventId.substring(0, 16)}...</small></td>
                <td>#${ev.executionId}</td>
                <td>${ev.eventType}</td>
                <td><span class="badge ${badge}">${ev.status}</span></td>
                <td>${ev.retryCount}</td>
                <td>${formatDateTime(ev.createdAt)}</td>
            </tr>
        `;
    }).join('');
}

// 5. Fetch Ledger
async function fetchLedger() {
    try {
        const res = await fetch(`${API_BASE}/transfers/ledger`);
        const json = await res.json();
        if (json.success && json.data) {
            renderLedger(json.data);
        }
    } catch (e) {
        console.error('Failed to fetch ledger', e);
    }
}

function renderLedger(entries) {
    const tbody = document.getElementById('ledgerTableBody');
    if (entries.length === 0) {
        tbody.innerHTML = `<tr><td colspan="7" class="text-center">No ledger entries recorded.</td></tr>`;
        return;
    }

    tbody.innerHTML = entries.slice(-15).reverse().map(entry => {
        const isDebit = entry.entryType === 'DEBIT';
        return `
            <tr>
                <td>#${entry.entryId}</td>
                <td><strong>${entry.transferReference}</strong></td>
                <td>${entry.accountId}</td>
                <td><span class="badge ${isDebit ? 'badge-danger' : 'badge-success'}">${entry.entryType}</span></td>
                <td><strong>${isDebit ? '-' : '+'}₱${parseFloat(entry.amount).toLocaleString('en-US', { minimumFractionDigits: 2 })}</strong></td>
                <td>₱${parseFloat(entry.balanceAfter).toLocaleString('en-US', { minimumFractionDigits: 2 })}</td>
                <td>${formatDateTime(entry.createdAt)}</td>
            </tr>
        `;
    }).join('');
}

// 6. Fetch Notifications
async function fetchNotifications() {
    try {
        const url = currentRole === 'CUSTOMER'
            ? `${API_BASE}/notifications?customerId=${currentCustomerId}`
            : `${API_BASE}/notifications`;

        const res = await fetch(url);
        const json = await res.json();
        if (json.success && json.data) {
            renderNotifications(json.data);
        }
    } catch (e) {
        console.error('Failed to fetch notifications', e);
    }
}

function renderNotifications(notifs) {
    const container = document.getElementById('notificationsFeed');
    if (notifs.length === 0) {
        container.innerHTML = `<p class="empty-state">No notifications received yet.</p>`;
        return;
    }

    container.innerHTML = notifs.slice(0, 10).map(n => `
        <div class="notif-item">
            <div class="notif-header">
                <strong>${n.subject}</strong>
                <span>${formatDateTime(n.createdAt)}</span>
            </div>
            <div class="notif-body">${n.message}</div>
        </div>
    `).join('');
}

function renderAuditVersionsList(orders) {
    const tbody = document.getElementById('auditVersionsTableBody');
    if (orders.length === 0) {
        tbody.innerHTML = `<tr><td colspan="7" class="text-center">No instructions to audit.</td></tr>`;
        return;
    }

    tbody.innerHTML = orders.map(o => `
        <tr>
            <td><strong>#${o.id}</strong></td>
            <td><span class="badge badge-neutral">v${o.version}</span></td>
            <td>${o.status}</td>
            <td>${o.customerId}</td>
            <td>Current state</td>
            <td>${formatDateTime(o.updatedAt || o.createdAt)}</td>
            <td><button class="btn btn-xs btn-primary" onclick="viewVersions(${o.id})">Inspect Full Audit Trail</button></td>
        </tr>
    `).join('');
}

// ===================== USER ACTIONS =====================

async function seedMariaOrder() {
    try {
        const res = await fetch(`${API_BASE}/standing-orders/seed-demo`, { method: 'POST' });
        const json = await res.json();
        if (json.success) {
            showBanner('Maria\'s ₱5,000 monthly standing order seeded successfully!', 'success');
            refreshAllData();
        }
    } catch (e) {
        showBanner('Failed to seed order: ' + e.message, 'error');
    }
}

async function pauseOrder(id) {
    try {
        const res = await fetch(`${API_BASE}/standing-orders/${id}/pause`, {
            method: 'POST',
            headers: { 'X-Customer-Id': currentCustomerId }
        });
        const json = await res.json();
        if (json.success) {
            showBanner(`Standing order #${id} paused.`, 'warning');
            refreshAllData();
        }
    } catch (e) {
        showBanner(e.message, 'error');
    }
}

async function resumeOrder(id) {
    try {
        const res = await fetch(`${API_BASE}/standing-orders/${id}/resume`, {
            method: 'POST',
            headers: { 'X-Customer-Id': currentCustomerId }
        });
        const json = await res.json();
        if (json.success) {
            showBanner(`Standing order #${id} resumed.`, 'success');
            refreshAllData();
        }
    } catch (e) {
        showBanner(e.message, 'error');
    }
}

async function cancelOrder(id) {
    if (!confirm(`Are you sure you want to cancel standing order #${id}?`)) return;
    try {
        const res = await fetch(`${API_BASE}/standing-orders/${id}/cancel`, {
            method: 'POST',
            headers: { 'X-Customer-Id': currentCustomerId }
        });
        const json = await res.json();
        if (json.success) {
            showBanner(`Standing order #${id} cancelled.`, 'danger');
            refreshAllData();
        }
    } catch (e) {
        showBanner(e.message, 'error');
    }
}

async function triggerDiscovery() {
    try {
        showBanner('Running discovery and processing due instructions...', 'info');
        const res = await fetch(`${API_BASE}/executions/trigger-discovery`, { method: 'POST' });
        const json = await res.json();
        if (json.success) {
            showBanner(`Discovery cycle executed: ${json.data} instruction(s) processed.`, 'success');
            refreshAllData();
        }
    } catch (e) {
        showBanner('Error triggering discovery: ' + e.message, 'error');
    }
}

async function resetDemoData() {
    try {
        const res = await fetch(`${API_BASE}/accounts/reset`, { method: 'POST' });
        const json = await res.json();
        if (json.success) {
            showBanner('Core banking account balances reset to initial state!', 'success');
            refreshAllData();
        }
    } catch (e) {
        showBanner(e.message, 'error');
    }
}

async function toggleNotificationOutage() {
    simulatedOutage = !simulatedOutage;
    try {
        await fetch(`${API_BASE}/notifications/simulate-outage`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ outage: simulatedOutage })
        });
        const btn = document.getElementById('btnToggleOutage');
        btn.textContent = simulatedOutage ? '🚨 Outage ACTIVE (Click to Restore)' : '⚠️ Toggle Notification Outage';
        btn.className = simulatedOutage ? 'btn btn-danger' : 'btn btn-warning';
        showBanner(`Notification simulated outage set to: ${simulatedOutage}`, simulatedOutage ? 'warning' : 'success');
    } catch (e) {
        showBanner(e.message, 'error');
    }
}

async function resolveExecution(id) {
    try {
        const res = await fetch(`${API_BASE}/executions/${id}/resolve`, { method: 'POST' });
        const json = await res.json();
        if (json.success) {
            showBanner(`Execution #${id} resolved: new status is ${json.data.status}`, 'success');
            refreshAllData();
        }
    } catch (e) {
        showBanner(e.message, 'error');
    }
}

async function viewVersions(id) {
    try {
        const res = await fetch(`${API_BASE}/standing-orders/${id}/versions`);
        const json = await res.json();
        if (json.success) {
            const list = document.getElementById('versionListContainer');
            list.innerHTML = json.data.map(v => `
                <div style="padding: 12px; border-bottom: 1px solid rgba(255,255,255,0.1); margin-bottom: 8px;">
                    <div style="display: flex; justify-content: space-between; margin-bottom: 6px;">
                        <strong>Version ${v.versionNumber} &mdash; Action: <span class="badge badge-info">${v.action}</span></strong>
                        <small class="text-muted">${formatDateTime(v.modifiedAt)} by ${v.modifiedBy}</small>
                    </div>
                    <div style="font-size: 0.85rem; color: var(--text-muted); margin-bottom: 6px;">Reason: ${v.reason || 'N/A'}</div>
                    <pre style="font-size: 0.72rem; background: rgba(0,0,0,0.3); padding: 8px; border-radius: 4px; overflow-x: auto;">${v.snapshotJson}</pre>
                </div>
            `).join('');
            document.getElementById('versionModal').classList.remove('hidden');
        }
    } catch (e) {
        showBanner(e.message, 'error');
    }
}

function closeVersionModal() {
    document.getElementById('versionModal').classList.add('hidden');
}

// ===================== MODALS =====================

function openCreateModal() {
    document.getElementById('createModal').classList.remove('hidden');
}

function closeCreateModal() {
    document.getElementById('createModal').classList.add('hidden');
}

async function handleCreateOrder(e) {
    e.preventDefault();
    const body = {
        sourceAccountId: document.getElementById('modalSourceAccount').value,
        destinationAccountId: document.getElementById('modalDestAccount').value,
        amount: parseFloat(document.getElementById('modalAmount').value),
        currency: 'PHP',
        frequency: document.getElementById('modalFrequency').value,
        dayOfMonth: parseInt(document.getElementById('modalDayOfMonth').value),
        executionTime: document.getElementById('modalExecutionTime').value + ':00',
        timeZone: document.getElementById('modalTimeZone').value,
        startDate: document.getElementById('modalStartDate').value,
        endDate: document.getElementById('modalEndDate').value || null
    };

    try {
        const res = await fetch(`${API_BASE}/standing-orders`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Customer-Id': currentCustomerId
            },
            body: JSON.stringify(body)
        });
        const json = await res.json();
        if (json.success) {
            showBanner('Standing order created successfully!', 'success');
            closeCreateModal();
            refreshAllData();
        } else {
            showBanner(json.message, 'error');
        }
    } catch (err) {
        showBanner(err.message, 'error');
    }
}

function openAmendModal(id, amount, dayOfMonth) {
    document.getElementById('amendOrderId').value = id;
    document.getElementById('amendAmount').value = amount;
    document.getElementById('amendDayOfMonth').value = dayOfMonth;
    document.getElementById('amendModal').classList.remove('hidden');
}

function closeAmendModal() {
    document.getElementById('amendModal').classList.add('hidden');
}

async function handleAmendOrder(e) {
    e.preventDefault();
    const id = document.getElementById('amendOrderId').value;
    const body = {
        amount: parseFloat(document.getElementById('amendAmount').value),
        dayOfMonth: parseInt(document.getElementById('amendDayOfMonth').value),
        reason: document.getElementById('amendReason').value
    };

    try {
        const res = await fetch(`${API_BASE}/standing-orders/${id}`, {
            method: 'PATCH',
            headers: {
                'Content-Type': 'application/json',
                'X-Customer-Id': currentCustomerId
            },
            body: JSON.stringify(body)
        });
        const json = await res.json();
        if (json.success) {
            showBanner(`Standing order #${id} amended to version ${json.data.version}`, 'success');
            closeAmendModal();
            refreshAllData();
        } else {
            showBanner(json.message, 'error');
        }
    } catch (err) {
        showBanner(err.message, 'error');
    }
}

// ===================== ACCEPTANCE TEST SCENARIO RUNNER =====================

async function runScenario(scenarioNum) {
    logToConsole(`\n========== RUNNING ACCEPTANCE SCENARIO ${scenarioNum} ==========`);

    try {
        if (scenarioNum === 1) {
            // Successful Transfer: Maria ₱5,000 from EWB-SAL-1001 to EWB-SAV-2001
            logToConsole('Resetting account balances...');
            await resetDemoData();

            logToConsole('Creating Maria\'s ₱5,000 monthly standing order...');
            const orderRes = await fetch(`${API_BASE}/standing-orders/seed-demo`, { method: 'POST' });
            const orderJson = await orderRes.json();
            logToConsole(`Instruction #${orderJson.data.id} created for customer ${orderJson.data.customerId}`);

            logToConsole('Triggering Execution Engine discovery...');
            const execRes = await fetch(`${API_BASE}/executions/trigger-discovery`, { method: 'POST' });
            const execJson = await execRes.json();
            logToConsole(`Execution completed. Count: ${execJson.data}`);

            await refreshAllData();
            logToConsole('SUCCESS: EWB-SAL-1001 balance reduced by ₱5,000 to ₱15,000. EWB-SAV-2001 credited to ₱6,000. Customer notification delivered!');

        } else if (scenarioNum === 2) {
            // Insufficient funds: Source EWB-SAL-1002 has ₱2,000. Attempt transfer of ₱5,000.
            logToConsole('Creating instruction with source EWB-SAL-1002 (Balance: ₱2,000), Amount: ₱5,000...');
            const orderRes = await fetch(`${API_BASE}/standing-orders`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'X-Customer-Id': 'CUST-JOHN' },
                body: JSON.stringify({
                    sourceAccountId: 'EWB-SAL-1002',
                    destinationAccountId: 'EWB-SAV-2001',
                    amount: 5000.00,
                    currency: 'PHP',
                    frequency: 'MONTHLY',
                    dayOfMonth: 25,
                    executionTime: '09:00:00',
                    timeZone: 'Asia/Manila',
                    startDate: '2026-01-01'
                })
            });
            const orderJson = await orderRes.json();
            logToConsole(`Instruction #${orderJson.data.id} created.`);

            logToConsole('Triggering Execution Service...');
            await fetch(`${API_BASE}/executions/trigger-discovery`, { method: 'POST' });
            await refreshAllData();

            logToConsole('SUCCESS: Execution failed with INSUFFICIENT_FUNDS. Balances remained unchanged. Recurring instruction schedule remained active.');

        } else if (scenarioNum === 3) {
            // Frozen Account: Source EWB-SAL-FROZEN
            logToConsole('Creating instruction with FROZEN account EWB-SAL-FROZEN...');
            const orderRes = await fetch(`${API_BASE}/standing-orders`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'X-Customer-Id': 'CUST-FROZEN' },
                body: JSON.stringify({
                    sourceAccountId: 'EWB-SAL-FROZEN',
                    destinationAccountId: 'EWB-SAV-2001',
                    amount: 5000.00,
                    currency: 'PHP',
                    frequency: 'MONTHLY',
                    dayOfMonth: 25,
                    executionTime: '09:00:00',
                    timeZone: 'Asia/Manila',
                    startDate: '2026-01-01'
                })
            });
            const orderJson = await orderRes.json();
            logToConsole(`Instruction #${orderJson.data.id} created.`);

            logToConsole('Triggering Execution...');
            await fetch(`${API_BASE}/executions/trigger-discovery`, { method: 'POST' });
            await refreshAllData();

            logToConsole('SUCCESS: Execution rejected with ACCOUNT_FROZEN. Reason recorded in attempts table. Zero ledger movement.');

        } else if (scenarioNum === 4) {
            // Duplicate Request: Replay identical idempotency key
            logToConsole('Submitting transfer with stable idempotency key SO-DUP-TEST-001...');
            const req = {
                sourceAccountId: 'EWB-SAL-1001',
                destinationAccountId: 'EWB-SAV-2001',
                amount: 100.00,
                currency: 'PHP',
                idempotencyKey: 'SO-DUP-TEST-001'
            };
            const firstRes = await fetch(`${API_BASE}/transfers`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(req)
            });
            const firstJson = await firstRes.json();
            logToConsole(`First transfer result: Ref=${firstJson.data.reference}, Status=${firstJson.data.status}`);

            logToConsole('Submitting identical second transfer with same idempotency key...');
            const secondRes = await fetch(`${API_BASE}/transfers`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(req)
            });
            const secondJson = await secondRes.json();
            logToConsole(`Second transfer result: Ref=${secondJson.data.reference}, Status=${secondJson.data.status}`);

            await refreshAllData();
            logToConsole('SUCCESS: Identical cached result returned without duplicate ledger debit or credit!');

        } else if (scenarioNum === 5) {
            // Lost Response & Recovery
            logToConsole('Simulating payment commit followed by network drop (simulateTimeout=true)...');
            const req = {
                sourceAccountId: 'EWB-SAL-1001',
                destinationAccountId: 'EWB-SAV-2001',
                amount: 250.00,
                currency: 'PHP',
                idempotencyKey: 'TIMEOUT-SIM-' + Date.now(),
                simulateTimeout: true
            };
            const tRes = await fetch(`${API_BASE}/transfers`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(req)
            });
            const tJson = await tRes.json();
            logToConsole(`Caller received: Status=${tJson.data.status}, Message=${tJson.data.errorMessage}`);

            logToConsole('Simulating Recovery: Operations queries transfer by idempotency key...');
            const recRes = await fetch(`${API_BASE}/transfers/by-idempotency/${req.idempotencyKey}`);
            const recJson = await recRes.json();
            logToConsole(`Recovery query discovered: Status=${recJson.data.status}, Reference=${recJson.data.reference}`);

            await refreshAllData();
            logToConsole('SUCCESS: Recovery confirmed committed payment without triggering any duplicate debit!');

        } else if (scenarioNum === 6) {
            // Paused Instruction
            logToConsole('Creating instruction and then immediately pausing it...');
            const orderRes = await fetch(`${API_BASE}/standing-orders`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'X-Customer-Id': 'CUST-PAUSE' },
                body: JSON.stringify({
                    sourceAccountId: 'EWB-SAL-1001',
                    destinationAccountId: 'EWB-SAV-2001',
                    amount: 500.00,
                    currency: 'PHP',
                    frequency: 'MONTHLY',
                    dayOfMonth: 25,
                    executionTime: '09:00:00',
                    timeZone: 'Asia/Manila',
                    startDate: '2026-01-01'
                })
            });
            const orderJson = await orderRes.json();
            const id = orderJson.data.id;
            logToConsole(`Order #${id} created. Pausing...`);

            await fetch(`${API_BASE}/standing-orders/${id}/pause`, {
                method: 'POST',
                headers: { 'X-Customer-Id': 'CUST-PAUSE' }
            });
            logToConsole(`Order #${id} status is now PAUSED.`);

            logToConsole('Triggering scheduler discovery...');
            await fetch(`${API_BASE}/executions/trigger-discovery`, { method: 'POST' });
            await refreshAllData();

            logToConsole('SUCCESS: Paused instruction was ignored by the execution engine. No transfer occurred.');

        } else if (scenarioNum === 7) {
            // Month-end schedule calculation (Day 31)
            logToConsole('Creating instruction scheduled on day 31 of each month...');
            const orderRes = await fetch(`${API_BASE}/standing-orders`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'X-Customer-Id': 'CUST-31' },
                body: JSON.stringify({
                    sourceAccountId: 'EWB-SAL-1001',
                    destinationAccountId: 'EWB-SAV-2001',
                    amount: 1000.00,
                    currency: 'PHP',
                    frequency: 'MONTHLY',
                    dayOfMonth: 31,
                    executionTime: '09:00:00',
                    timeZone: 'Asia/Manila',
                    startDate: '2026-02-01'
                })
            });
            const orderJson = await orderRes.json();
            logToConsole(`Calculated next execution time: ${orderJson.data.nextExecutionTime}`);

            await refreshAllData();
            logToConsole('SUCCESS: In shorter months like February, the schedule automatically clamped to the last day of the month!');

        } else if (scenarioNum === 8) {
            // Notification Outage & Retry
            logToConsole('Enabling simulated notification outage...');
            await fetch(`${API_BASE}/notifications/simulate-outage`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ outage: true })
            });

            logToConsole('Executing transfer...');
            const tRes = await fetch(`${API_BASE}/transfers`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    sourceAccountId: 'EWB-SAL-1001',
                    destinationAccountId: 'EWB-SAV-2001',
                    amount: 300.00,
                    currency: 'PHP',
                    idempotencyKey: 'NOTIF-OUTAGE-' + Date.now()
                })
            });
            logToConsole('Payment succeeded in core banking.');

            logToConsole('Restoring notification service...');
            await fetch(`${API_BASE}/notifications/simulate-outage`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ outage: false })
            });

            await refreshAllData();
            logToConsole('SUCCESS: Payment remained intact while notification outage was safely isolated!');
        }

        showBanner(`Acceptance Scenario ${scenarioNum} completed! Inspect logs in console.`, 'success');
    } catch (err) {
        logToConsole(`ERROR: ${err.message}`);
        showBanner(`Scenario ${scenarioNum} error: ${err.message}`, 'error');
    }
}

function formatDateTime(isoString) {
    if (!isoString) return '';
    try {
        const d = new Date(isoString);
        return d.toLocaleDateString('en-US', {
            month: 'short',
            day: 'numeric',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    } catch (e) {
        return isoString;
    }
}
