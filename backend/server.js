require('dotenv').config();
const path = require('path');
const express = require('express');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const rateLimit = require('express-rate-limit');
const { pool, init } = require('./db');
const V = require('./validate');

const PROD = process.env.NODE_ENV === 'production';
const SECRET = process.env.JWT_SECRET || (PROD ? null : 'dev-only-secret');
if (!SECRET) { console.error('JWT_SECRET must be set in production.'); process.exit(1); }

const app = express();
app.set('trust proxy', 1);
app.use(express.json({ limit: '1mb' }));

// ---------- helpers ----------
const wrap = (fn) => (req, res, next) => fn(req, res, next).catch(next);
const fail = (res, code, msg) => res.status(code).json({ error: msg });

function auth(req, res, next) {
  const h = req.headers.authorization || '';
  try {
    req.user = jwt.verify(h.replace(/^Bearer /, ''), SECRET);
    next();
  } catch {
    fail(res, 401, 'Please log in again.');
  }
}
const allow = (...roles) => (req, res, next) =>
  roles.includes(req.user.role) ? next() : fail(res, 403, 'You do not have permission for this action.');

const publicUser = (u) => ({
  id: u.id, name: u.name, email: u.email, role: u.role, phone: u.phone || '',
  department: u.department || '', birthday: u.birthday || '', has_avatar: !!u.has_avatar,
});
const USER_COLS = `id, name, email, role, phone, department, to_char(birthday,'YYYY-MM-DD') AS birthday, (avatar IS NOT NULL) AS has_avatar`;

// ---------- health ----------
app.get('/healthz', (req, res) => res.send('ok'));

// ---------- auth ----------
const loginLimiter = rateLimit({ windowMs: 15 * 60 * 1000, max: 30, standardHeaders: true, legacyHeaders: false,
  message: { error: 'Too many login attempts. Try again later.' } });

app.post('/api/auth/login', loginLimiter, wrap(async (req, res) => {
  const email = V.clean(req.body.email).toLowerCase();
  const password = String(req.body.password || '');
  const role = V.clean(req.body.role);
  if (!email || !password) return fail(res, 400, 'Please enter your email and password.');
  const { rows } = await pool.query('SELECT * FROM users WHERE LOWER(email)=$1 AND role=$2', [email, role]);
  const u = rows[0];
  if (!u || !(await bcrypt.compare(password, u.password_hash))) return fail(res, 401, 'Invalid email, password, or role.');
  const token = jwt.sign({ id: u.id, role: u.role, name: u.name }, SECRET, { expiresIn: '8h' });
  res.json({ token, user: { id: u.id, name: u.name, role: u.role } });
}));

// ---------- profile ----------
app.get('/api/me', auth, wrap(async (req, res) => {
  const { rows } = await pool.query(`SELECT ${USER_COLS}, avatar FROM users WHERE id=$1`, [req.user.id]);
  if (!rows[0]) return fail(res, 404, 'User not found.');
  res.json({ ...publicUser(rows[0]), avatar: rows[0].avatar || null });
}));

app.put('/api/me', auth, wrap(async (req, res) => {
  const phone = V.clean(req.body.phone).slice(0, 15);
  const department = V.clean(req.body.department).slice(0, 50);
  const birthday = V.clean(req.body.birthday);
  if (!V.isBirthday(birthday)) return fail(res, 400, 'Birthday must be in yyyy-mm-dd format and cannot be in the future.');
  let avatar = req.body.avatar;
  if (avatar !== undefined && avatar !== null) {
    if (typeof avatar !== 'string' || !/^data:image\/(png|jpe?g);base64,/.test(avatar) || avatar.length > 400000)
      return fail(res, 400, 'Image must be a small JPG or PNG.');
  }
  await pool.query(
    `UPDATE users SET phone=$1, department=$2, birthday=$3, avatar=COALESCE($4, avatar) WHERE id=$5`,
    [phone, department, birthday || null, avatar ?? null, req.user.id]
  );
  res.json({ ok: true });
}));

app.post('/api/me/password', auth, wrap(async (req, res) => {
  const { current, next } = req.body;
  if (!next || String(next).length < 6) return fail(res, 400, 'New password must be at least 6 characters.');
  const { rows } = await pool.query('SELECT password_hash FROM users WHERE id=$1', [req.user.id]);
  if (!rows[0] || !(await bcrypt.compare(String(current || ''), rows[0].password_hash)))
    return fail(res, 400, 'Current password is incorrect.');
  await pool.query('UPDATE users SET password_hash=$1 WHERE id=$2', [await bcrypt.hash(String(next), 10), req.user.id]);
  res.json({ ok: true });
}));

// ---------- users (records) ----------
app.get('/api/users', auth, wrap(async (req, res) => {
  const q = `%${V.clean(req.query.q).toLowerCase()}%`;
  let sql = `SELECT ${USER_COLS} FROM users WHERE (LOWER(id) LIKE $1 OR LOWER(name) LIKE $1 OR LOWER(email) LIKE $1)`;
  const params = [q];
  if (req.user.role === 'Student') { sql += ' AND id=$2'; params.push(req.user.id); }
  const { rows } = await pool.query(sql + ' ORDER BY id', params);
  res.json(rows.map(publicUser));
}));

function readUserBody(b, isNew) {
  const v = {
    id: V.clean(b.id), name: V.clean(b.name).slice(0, 100), email: V.clean(b.email),
    role: V.clean(b.role), phone: V.clean(b.phone).slice(0, 15), department: V.clean(b.department).slice(0, 50),
    birthday: V.clean(b.birthday), password: String(b.password || ''),
  };
  if (isNew && !/^[A-Za-z0-9_-]{1,20}$/.test(v.id)) return { error: 'ID is required (letters, numbers, - and _ only, max 20).' };
  if (!v.name || !v.email) return { error: 'ID, name, and email are required.' };
  if (!V.isEmail(v.email)) return { error: 'Enter a valid email address.' };
  if (!V.isBirthday(v.birthday)) return { error: 'Birthday must be in yyyy-mm-dd format and cannot be in the future.' };
  if (!V.ROLES.includes(v.role)) return { error: 'Please select a valid role.' };
  if (isNew && v.password.length < 6) return { error: 'Password (min 6 characters) is required for a new user.' };
  if (!isNew && v.password && v.password.length < 6) return { error: 'New password must be at least 6 characters.' };
  return { value: v };
}
const dupMsg = (e) => (/users_email_uq/.test(e.constraint || '') ? 'Email already exists.' : 'User ID already exists.');

app.post('/api/users', auth, allow('Admin'), wrap(async (req, res) => {
  const { error, value: v } = readUserBody(req.body, true);
  if (error) return fail(res, 400, error);
  try {
    await pool.query(
      `INSERT INTO users (id,name,email,password_hash,role,phone,department,birthday) VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`,
      [v.id, v.name, v.email, await bcrypt.hash(v.password, 10), v.role, v.phone, v.department, v.birthday || null]
    );
  } catch (e) { if (e.code === '23505') return fail(res, 409, dupMsg(e)); throw e; }
  res.status(201).json({ ok: true });
}));

app.put('/api/users/:id', auth, allow('Admin'), wrap(async (req, res) => {
  const { error, value: v } = readUserBody(req.body, false);
  if (error) return fail(res, 400, error);
  const { rows } = await pool.query('SELECT role FROM users WHERE id=$1', [req.params.id]);
  if (!rows[0]) return fail(res, 404, 'User not found.');
  const role = req.params.id === req.user.id ? rows[0].role : v.role; // cannot change own role
  try {
    await pool.query(
      `UPDATE users SET name=$1,email=$2,role=$3,phone=$4,department=$5,birthday=$6 WHERE id=$7`,
      [v.name, v.email, role, v.phone, v.department, v.birthday || null, req.params.id]
    );
    if (v.password) await pool.query('UPDATE users SET password_hash=$1 WHERE id=$2', [await bcrypt.hash(v.password, 10), req.params.id]);
  } catch (e) { if (e.code === '23505') return fail(res, 409, dupMsg(e)); throw e; }
  res.json({ ok: true });
}));

app.delete('/api/users/:id', auth, allow('Admin'), wrap(async (req, res) => {
  if (req.params.id === req.user.id) return fail(res, 400, 'You cannot delete the account you are currently logged in with.');
  const r = await pool.query('DELETE FROM users WHERE id=$1', [req.params.id]); // results cascade
  if (!r.rowCount) return fail(res, 404, 'User not found.');
  res.json({ ok: true });
}));

// ---------- results ----------
const RESULT_SQL = `SELECT r.id, r.student_id, u.name AS student_name, r.exam, r.year, r.semester, r.gpa::float AS gpa, r.grade
  FROM results r JOIN users u ON u.id = r.student_id`;

app.get('/api/results', auth, wrap(async (req, res) => {
  const q = `%${V.clean(req.query.q).toLowerCase()}%`;
  let sql = `${RESULT_SQL} WHERE (LOWER(r.student_id) LIKE $1 OR LOWER(u.name) LIKE $1)`;
  const params = [q];
  if (req.user.role === 'Student') { sql += ' AND r.student_id=$2'; params.push(req.user.id); }
  const { rows } = await pool.query(sql + ' ORDER BY r.student_id, r.year, r.semester, r.exam', params);
  res.json(rows);
}));

async function checkStudent(id) {
  const { rows } = await pool.query('SELECT role FROM users WHERE id=$1', [id]);
  return rows[0] && rows[0].role === 'Student';
}
const DUP_RESULT = 'A result for this student, exam, year, and semester already exists.';

app.post('/api/results', auth, allow('Admin', 'Teacher'), wrap(async (req, res) => {
  const { error, value: v } = V.validateResult(req.body);
  if (error) return fail(res, 400, error);
  if (!(await checkStudent(v.student_id))) return fail(res, 400, 'Student ID must belong to an existing Student.');
  try {
    await pool.query('INSERT INTO results (student_id,exam,year,semester,gpa,grade) VALUES ($1,$2,$3,$4,$5,$6)',
      [v.student_id, v.exam, v.year, v.semester, v.gpa, v.grade]);
  } catch (e) { if (e.code === '23505') return fail(res, 409, DUP_RESULT); throw e; }
  res.status(201).json({ ok: true });
}));

app.put('/api/results/:id', auth, allow('Admin', 'Teacher'), wrap(async (req, res) => {
  const { error, value: v } = V.validateResult(req.body);
  if (error) return fail(res, 400, error);
  if (!(await checkStudent(v.student_id))) return fail(res, 400, 'Student ID must belong to an existing Student.');
  try {
    const r = await pool.query('UPDATE results SET student_id=$1,exam=$2,year=$3,semester=$4,gpa=$5,grade=$6 WHERE id=$7',
      [v.student_id, v.exam, v.year, v.semester, v.gpa, v.grade, req.params.id]);
    if (!r.rowCount) return fail(res, 404, 'Result not found.');
  } catch (e) { if (e.code === '23505') return fail(res, 409, DUP_RESULT); throw e; }
  res.json({ ok: true });
}));

app.delete('/api/results/:id', auth, allow('Admin', 'Teacher'), wrap(async (req, res) => {
  const r = await pool.query('DELETE FROM results WHERE id=$1', [req.params.id]);
  if (!r.rowCount) return fail(res, 404, 'Result not found.');
  res.json({ ok: true });
}));

app.get('/api/results-export', auth, allow('Admin', 'Teacher'), wrap(async (req, res) => {
  const { rows } = await pool.query(RESULT_SQL + ' ORDER BY r.student_id, r.year, r.semester');
  const esc = (s) => `"${String(s).replace(/"/g, '""')}"`;
  const csv = ['Student ID,Name,Exam,Year,Semester,GPA,Grade']
    .concat(rows.map((r) => [r.student_id, r.student_name, r.exam, r.year, r.semester, r.gpa, r.grade].map(esc).join(','))).join('\n');
  res.set({ 'Content-Type': 'text/csv', 'Content-Disposition': 'attachment; filename="results.csv"' }).send(csv);
}));

// ---------- dashboard ----------
app.get('/api/stats', auth, wrap(async (req, res) => {
  if (req.user.role === 'Student') {
    const { rows } = await pool.query(
      'SELECT COUNT(*)::int AS results, ROUND(AVG(gpa),2)::float AS cgpa FROM results WHERE student_id=$1', [req.user.id]);
    const grades = await pool.query('SELECT grade, COUNT(*)::int AS n FROM results WHERE student_id=$1 GROUP BY grade ORDER BY grade', [req.user.id]);
    return res.json({ role: 'Student', results: rows[0].results, cgpa: rows[0].cgpa, grades: grades.rows });
  }
  const one = async (sql) => (await pool.query(sql)).rows;
  const [counts] = await one(`SELECT COUNT(*)::int AS users,
      COUNT(*) FILTER (WHERE role='Student')::int AS students,
      COUNT(*) FILTER (WHERE role='Teacher')::int AS teachers,
      COUNT(*) FILTER (WHERE role='Admin')::int AS admins FROM users`);
  const [agg] = await one('SELECT COUNT(*)::int AS results, ROUND(AVG(gpa),2)::float AS avg_gpa FROM results');
  const byDept = await one(`SELECT COALESCE(NULLIF(department,''),'Unassigned') AS department, COUNT(*)::int AS n
      FROM users WHERE role='Student' GROUP BY 1 ORDER BY n DESC`);
  const grades = await one('SELECT grade, COUNT(*)::int AS n FROM results GROUP BY grade ORDER BY grade');
  const top = await one(`SELECT u.id, u.name, ROUND(AVG(r.gpa),2)::float AS cgpa FROM results r
      JOIN users u ON u.id=r.student_id GROUP BY u.id, u.name ORDER BY cgpa DESC LIMIT 5`);
  res.json({ role: req.user.role, ...counts, ...agg, byDept, grades, top });
}));

// ---------- frontend ----------
app.use(express.static(path.join(__dirname, '..', 'frontend')));
app.use('/api', (req, res) => fail(res, 404, 'Not found.'));

// ---------- errors ----------
app.use((err, req, res, next) => {
  console.error(err);
  fail(res, 500, 'Something went wrong on the server.');
});

const PORT = process.env.PORT || 3000;
init().then(() => app.listen(PORT, () => console.log(`Server running on port ${PORT}`)))
  .catch((e) => { console.error('Startup failed:', e.message); process.exit(1); });
