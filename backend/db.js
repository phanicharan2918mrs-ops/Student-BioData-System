const { Pool } = require('pg');
const bcrypt = require('bcryptjs');

const url = process.env.DATABASE_URL;
if (!url) {
  console.error('DATABASE_URL is not set. See .env.example');
  process.exit(1);
}
const local = /localhost|127\.0\.0\.1/.test(url);
const pool = new Pool({ connectionString: url, ssl: local ? false : { rejectUnauthorized: false } });

async function init() {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS users (
      id            VARCHAR(20)  PRIMARY KEY,
      name          VARCHAR(100) NOT NULL,
      email         VARCHAR(100) NOT NULL,
      password_hash VARCHAR(100) NOT NULL,
      role          VARCHAR(10)  NOT NULL CHECK (role IN ('Admin','Teacher','Student')),
      phone         VARCHAR(15)  DEFAULT '',
      department    VARCHAR(50)  DEFAULT '',
      birthday      DATE,
      avatar        TEXT
    );
    CREATE UNIQUE INDEX IF NOT EXISTS users_email_uq ON users (LOWER(email));
    CREATE TABLE IF NOT EXISTS results (
      id         SERIAL PRIMARY KEY,
      student_id VARCHAR(20) NOT NULL REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE,
      exam       VARCHAR(100) NOT NULL,
      year       INT NOT NULL,
      semester   INT NOT NULL,
      gpa        NUMERIC(4,2) NOT NULL CHECK (gpa >= 0 AND gpa <= 10),
      grade      VARCHAR(2) NOT NULL
    );
    CREATE UNIQUE INDEX IF NOT EXISTS results_uq ON results (student_id, LOWER(exam), year, semester);
  `);

  const { rows } = await pool.query('SELECT COUNT(*)::int AS n FROM users');
  if (rows[0].n === 0) await seed();
}

async function seed() {
  const h = (p) => bcrypt.hashSync(p, 10);
  const users = [
    ['A001', 'Admin', 'admin@example.com', 'admin123', 'Admin', 'CSE'],
    ['T001', 'Tom Teacher', 'tom@faculty.edu', 'teach123', 'Teacher', 'CSE'],
    ['S001', 'Sara Student', 'sara@student.edu', 'pass123', 'Student', 'CSE-AIML'],
    ['S002', 'Ravi Kumar', 'ravi@student.edu', 'pass123', 'Student', 'CSE-AIML'],
    ['S003', 'Anita Rao', 'anita@student.edu', 'pass123', 'Student', 'ECE'],
    ['S004', 'John Paul', 'john@student.edu', 'pass123', 'Student', 'ECE'],
  ];
  for (const [id, name, email, pw, role, dept] of users) {
    await pool.query(
      'INSERT INTO users (id,name,email,password_hash,role,department) VALUES ($1,$2,$3,$4,$5,$6)',
      [id, name, email, h(pw), role, dept]
    );
  }
  const results = [
    ['S001', 'Mid Term', 2025, 1, 8.7, 'A'], ['S001', 'End Term', 2025, 1, 9.1, 'O'],
    ['S002', 'Mid Term', 2025, 1, 7.4, 'B+'], ['S002', 'End Term', 2025, 1, 7.9, 'B+'],
    ['S003', 'Mid Term', 2025, 1, 8.2, 'A'], ['S003', 'End Term', 2025, 1, 8.8, 'A'],
    ['S004', 'Mid Term', 2025, 1, 6.5, 'B'], ['S004', 'End Term', 2025, 1, 6.9, 'B'],
  ];
  for (const r of results) {
    await pool.query('INSERT INTO results (student_id,exam,year,semester,gpa,grade) VALUES ($1,$2,$3,$4,$5,$6)', r);
  }
  console.log('Seeded demo accounts and sample results.');
}

module.exports = { pool, init };
