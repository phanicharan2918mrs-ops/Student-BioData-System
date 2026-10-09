'use strict';
// ---------- helpers ----------
const $ = (s, r = document) => r.querySelector(s);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const ROLES = ['Admin', 'Teacher', 'Student'];
const GRADES = ['O', 'A+', 'A', 'B+', 'B', 'C', 'D', 'E', 'F', 'NA'];

let token = localStorage.getItem('token');
let session = JSON.parse(localStorage.getItem('session') || 'null');
let currentTab = 'dashboard';

async function api(path, opts = {}) {
  const res = await fetch('/api' + path, {
    method: opts.method || 'GET',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: opts.body ? JSON.stringify(opts.body) : undefined,
  });
  if (res.status === 401 && path !== '/auth/login') { logout(); throw new Error('Session expired. Please log in again.'); }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || 'Request failed');
  return data;
}

function toast(msg) {
  const t = $('#toast');
  t.textContent = msg; t.classList.remove('hidden');
  clearTimeout(toast.t); toast.t = setTimeout(() => t.classList.add('hidden'), 2500);
}

// Modal form. onSave(data) may throw to show an error inside the dialog.
function formDialog(title, fields, values, onSave) {
  const dlg = document.createElement('dialog');
  const inputs = fields.map((f) => {
    const v = values[f.name] ?? '';
    const dis = f.disabled ? 'disabled' : '';
    const control = f.type === 'select'
      ? `<select name="${f.name}" ${dis}>${f.options.map((o) => `<option ${String(v) === String(o) ? 'selected' : ''}>${esc(o)}</option>`).join('')}</select>`
      : `<input name="${f.name}" type="${f.type || 'text'}" value="${esc(v)}" placeholder="${esc(f.placeholder || '')}" ${dis} ${f.step ? `step="${f.step}"` : ''}>`;
    return `<label>${esc(f.label)}${control}</label>`;
  }).join('');
  dlg.innerHTML = `<form><h3>${esc(title)}</h3><div class="fields">${inputs}</div><p class="err"></p>
    <div class="row"><button type="button" class="ghost" data-cancel>Cancel</button><button class="primary">Save</button></div></form>`;
  document.body.appendChild(dlg);
  const form = $('form', dlg), err = $('.err', dlg);
  $('[data-cancel]', dlg).onclick = () => dlg.close();
  dlg.addEventListener('close', () => dlg.remove());
  form.onsubmit = async (e) => {
    e.preventDefault();
    const data = {};
    fields.forEach((f) => { data[f.name] = f.disabled ? values[f.name] : form.elements[f.name].value; });
    const btn = $('.primary', dlg); btn.disabled = true; err.textContent = '';
    try { await onSave(data); dlg.close(); } catch (ex) { err.textContent = ex.message; btn.disabled = false; }
  };
  dlg.showModal();
}

// ---------- theme ----------
function applyTheme(t) { document.documentElement.dataset.theme = t; localStorage.setItem('theme', t); }
applyTheme(localStorage.getItem('theme') || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'));
$('#themeBtn').onclick = () => applyTheme(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark');

// ---------- auth ----------
function logout() {
  token = null; session = null;
  localStorage.removeItem('token'); localStorage.removeItem('session');
  show();
}
$('#logoutBtn').onclick = logout;

$('#loginForm').onsubmit = async (e) => {
  e.preventDefault();
  const f = e.target, btn = $('button', f), err = $('#loginErr');
  btn.disabled = true; err.textContent = '';
  try {
    const d = await api('/auth/login', { method: 'POST', body: { email: f.email.value, password: f.password.value, role: f.role.value } });
    token = d.token; session = d.user;
    localStorage.setItem('token', token); localStorage.setItem('session', JSON.stringify(session));
    f.password.value = ''; currentTab = 'dashboard'; show();
  } catch (ex) { err.textContent = ex.message; }
  btn.disabled = false;
};

// ---------- shell ----------
function show() {
  const loggedIn = !!(token && session);
  $('#loginView').classList.toggle('hidden', loggedIn);
  $('#appView').classList.toggle('hidden', !loggedIn);
  if (!loggedIn) return;
  $('#who').textContent = `${session.name} (${session.role})`;
  const tabs = [['dashboard', 'Dashboard'], ['profile', 'Profile'], ['records', 'Records'], ['results', 'Results']];
  $('#tabs').innerHTML = tabs.map(([k, l]) => `<button data-tab="${k}" class="${k === currentTab ? 'active' : ''}">${l}</button>`).join('');
  $('#tabs').onclick = (e) => { const k = e.target.dataset.tab; if (k) { currentTab = k; show(); } };
  render();
}

async function render() {
  const page = $('#page');
  page.innerHTML = '<p class="muted">Loading...</p>';
  try { await ({ dashboard, profile, records, results })[currentTab](page); }
  catch (ex) { page.innerHTML = `<p class="err">${esc(ex.message)}</p>`; }
}

// ---------- dashboard ----------
const bars = (rows, label, total) => {
  const max = Math.max(1, ...rows.map((r) => r.n));
  return `<div class="bars">${rows.map((r) => `<div class="bar"><span>${esc(r[label])}</span>
    <div class="track"><div class="fill" style="width:${(r.n / max) * 100}%"></div></div><b>${r.n}</b></div>`).join('') || '<p class="muted">No data yet.</p>'}</div>`;
};

async function dashboard(page) {
  const s = await api('/stats');
  if (s.role === 'Student') {
    page.innerHTML = `<h2>Welcome, ${esc(session.name)}</h2>
      <div class="stats">
        <div class="card stat"><span class="muted">Your CGPA</span><b>${s.cgpa ?? '-'}</b></div>
        <div class="card stat"><span class="muted">Results recorded</span><b>${s.results}</b></div>
      </div>
      <div class="card"><strong>Your grades</strong>${bars(s.grades, 'grade')}</div>`;
    return;
  }
  page.innerHTML = `<h2>Dashboard</h2>
    <div class="stats">
      <div class="card stat"><span class="muted">Students</span><b>${s.students}</b></div>
      <div class="card stat"><span class="muted">Teachers</span><b>${s.teachers}</b></div>
      <div class="card stat"><span class="muted">Results</span><b>${s.results}</b></div>
      <div class="card stat"><span class="muted">Average GPA</span><b>${s.avg_gpa ?? '-'}</b></div>
    </div>
    <div class="grid2">
      <div class="card"><strong>Students by department</strong>${bars(s.byDept, 'department')}</div>
      <div class="card"><strong>Grade distribution</strong>${bars(s.grades, 'grade')}</div>
    </div>
    <div class="card"><strong>Top students (CGPA)</strong>
      ${s.top.map((t, i) => `<div class="rank"><span>${i + 1}. ${esc(t.name)} <span class="muted">${esc(t.id)}</span></span><b>${t.cgpa}</b></div>`).join('') || '<p class="muted">No results yet.</p>'}
    </div>`;
}

// ---------- profile ----------
async function profile(page) {
  const m = await api('/me');
  page.innerHTML = `<h2>My Profile</h2>
    <div class="card profile">
      <div>
        <div class="readonly" style="margin-bottom:1rem">
          <span>ID</span><span>${esc(m.id)}</span><span>Name</span><span>${esc(m.name)}</span>
          <span>Email</span><span>${esc(m.email)}</span><span>Role</span><span>${esc(m.role)}</span>
        </div>
        <form id="pf">
          <label>Phone<input name="phone" value="${esc(m.phone)}" maxlength="15"></label>
          <label>Department<input name="department" value="${esc(m.department)}" maxlength="50"></label>
          <label>Birthday<input name="birthday" type="date" value="${esc(m.birthday)}"></label>
          <p class="err" id="pfErr"></p>
          <div><button class="primary">Save changes</button> <button type="button" class="ghost" id="pwBtn">Change password</button></div>
        </form>
      </div>
      <div class="side">
        ${m.avatar ? `<img class="avatar" src="${esc(m.avatar)}" alt="Profile photo">` : `<div class="avatar">${esc(m.name[0])}</div>`}
        <input type="file" id="photo" accept="image/png,image/jpeg" hidden>
        <button id="photoBtn">Upload image</button>
      </div>
    </div>`;
  $('#pf').onsubmit = async (e) => {
    e.preventDefault();
    const f = e.target;
    try { await api('/me', { method: 'PUT', body: { phone: f.phone.value, department: f.department.value, birthday: f.birthday.value } }); toast('Profile saved'); $('#pfErr').textContent = ''; }
    catch (ex) { $('#pfErr').textContent = ex.message; }
  };
  $('#pwBtn').onclick = () => formDialog('Change password',
    [{ name: 'current', label: 'Current password', type: 'password' }, { name: 'next', label: 'New password (min 6)', type: 'password' }], {},
    async (d) => { await api('/me/password', { method: 'POST', body: d }); toast('Password changed'); });
  $('#photoBtn').onclick = () => $('#photo').click();
  $('#photo').onchange = async (e) => {
    const file = e.target.files[0]; if (!file) return;
    try { await api('/me', { method: 'PUT', body: { ...Object.fromEntries(new FormData($('#pf'))), avatar: await resizeImage(file) } }); toast('Image updated'); render(); }
    catch (ex) { toast(ex.message); }
  };
}

function resizeImage(file) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      const size = 240, c = document.createElement('canvas');
      c.width = c.height = size;
      const s = Math.min(img.width, img.height);
      c.getContext('2d').drawImage(img, (img.width - s) / 2, (img.height - s) / 2, s, s, 0, 0, size, size);
      resolve(c.toDataURL('image/jpeg', 0.85));
    };
    img.onerror = () => reject(new Error('The selected file is not a valid image.'));
    img.src = URL.createObjectURL(file);
  });
}

// ---------- records ----------
async function records(page) {
  const admin = session.role === 'Admin';
  page.innerHTML = `<h2>Records</h2>
    <div class="toolbar"><input id="q" placeholder="Search by ID, name or email">${admin ? '<button class="primary" id="add">Add user</button>' : ''}</div>
    <div class="card tablewrap"><table class="data"><thead><tr><th>ID</th><th>Name</th><th>Email</th><th>Role</th><th>Phone</th><th>Department</th><th>Birthday</th>${admin ? '<th></th>' : ''}</tr></thead><tbody id="rows"></tbody></table></div>`;
  let list = [];
  const load = async () => {
    list = await api('/users?q=' + encodeURIComponent($('#q').value));
    $('#rows').innerHTML = list.map((u) => `<tr><td>${esc(u.id)}</td><td>${esc(u.name)}</td><td>${esc(u.email)}</td><td>${esc(u.role)}</td><td>${esc(u.phone)}</td><td>${esc(u.department)}</td><td>${esc(u.birthday)}</td>
      ${admin ? `<td class="actions"><button data-edit="${esc(u.id)}">Edit</button><button class="danger" data-del="${esc(u.id)}">Delete</button></td>` : ''}</tr>`).join('')
      || `<tr><td colspan="8" class="empty">No records found.</td></tr>`;
  };
  await load();
  let timer; $('#q').oninput = () => { clearTimeout(timer); timer = setTimeout(load, 250); };
  if (!admin) return;

  const fields = (isNew) => [
    { name: 'id', label: 'ID', disabled: !isNew }, { name: 'name', label: 'Name' }, { name: 'email', label: 'Email', type: 'email' },
    { name: 'password', label: isNew ? 'Password (min 6)' : 'New password (blank = keep)', type: 'password' },
    { name: 'role', label: 'Role', type: 'select', options: ROLES }, { name: 'phone', label: 'Phone' },
    { name: 'department', label: 'Department' }, { name: 'birthday', label: 'Birthday', type: 'date' },
  ];
  $('#add').onclick = () => formDialog('Add user', fields(true), { role: 'Student' },
    async (d) => { await api('/users', { method: 'POST', body: d }); toast('User added'); await load(); });
  $('#rows').onclick = async (e) => {
    const eid = e.target.dataset.edit, did = e.target.dataset.del;
    if (eid) {
      const u = list.find((x) => x.id === eid);
      formDialog('Edit user', fields(false), u, async (d) => { await api('/users/' + encodeURIComponent(eid), { method: 'PUT', body: d }); toast('User updated'); await load(); });
    } else if (did && confirm(`Delete ${did} and all of their results?`)) {
      try { await api('/users/' + encodeURIComponent(did), { method: 'DELETE' }); toast('User deleted'); await load(); } catch (ex) { toast(ex.message); }
    }
  };
}

// ---------- results ----------
async function results(page) {
  const staff = session.role !== 'Student';
  page.innerHTML = `<h2>Results</h2>
    <div class="toolbar"><input id="q" placeholder="Search by student ID or name">
      ${staff ? '<button class="primary" id="add">Add result</button><button id="csv">Export CSV</button>' : '<span class="muted">You can view your own results only.</span>'}</div>
    <div class="card tablewrap"><table class="data"><thead><tr><th>Student ID</th><th>Name</th><th>Exam</th><th>Year</th><th>Semester</th><th>GPA</th><th>Grade</th>${staff ? '<th></th>' : ''}</tr></thead><tbody id="rows"></tbody></table></div>`;
  let list = [];
  const load = async () => {
    list = await api('/results?q=' + encodeURIComponent($('#q').value));
    $('#rows').innerHTML = list.map((r) => `<tr><td>${esc(r.student_id)}</td><td>${esc(r.student_name)}</td><td>${esc(r.exam)}</td><td>${r.year}</td><td>${r.semester}</td><td>${r.gpa.toFixed(2)}</td><td>${esc(r.grade)}</td>
      ${staff ? `<td class="actions"><button data-edit="${r.id}">Edit</button><button class="danger" data-del="${r.id}">Delete</button></td>` : ''}</tr>`).join('')
      || `<tr><td colspan="8" class="empty">No results found.</td></tr>`;
  };
  await load();
  let timer; $('#q').oninput = () => { clearTimeout(timer); timer = setTimeout(load, 250); };
  if (!staff) return;

  const fields = [
    { name: 'student_id', label: 'Student ID' }, { name: 'exam', label: 'Exam' }, { name: 'year', label: 'Year', type: 'number' },
    { name: 'semester', label: 'Semester (1-8 or I-VIII)' }, { name: 'gpa', label: 'GPA (0-10)', type: 'number', step: '0.01' },
    { name: 'grade', label: 'Grade', type: 'select', options: GRADES },
  ];
  $('#add').onclick = () => formDialog('Add result', fields, { year: new Date().getFullYear(), grade: 'A' },
    async (d) => { await api('/results', { method: 'POST', body: d }); toast('Result added'); await load(); });
  $('#csv').onclick = async () => {
    const res = await fetch('/api/results-export', { headers: { Authorization: 'Bearer ' + token } });
    if (!res.ok) return toast('Export failed');
    const a = document.createElement('a');
    a.href = URL.createObjectURL(await res.blob()); a.download = 'results.csv'; a.click(); URL.revokeObjectURL(a.href);
  };
  $('#rows').onclick = async (e) => {
    const eid = e.target.dataset.edit, did = e.target.dataset.del;
    if (eid) {
      const r = list.find((x) => String(x.id) === eid);
      formDialog('Edit result', fields, r, async (d) => { await api('/results/' + eid, { method: 'PUT', body: d }); toast('Result updated'); await load(); });
    } else if (did && confirm('Delete this result?')) {
      try { await api('/results/' + did, { method: 'DELETE' }); toast('Result deleted'); await load(); } catch (ex) { toast(ex.message); }
    }
  };
}

show();
