# Student Bio-Data System (Web Version)

A full-stack web application for managing student records and exam results, with role-based access for **Admin**, **Teacher** and **Student**. It is the web upgrade of our Java Swing desktop project.

**Live demo:** `<paste your Render URL here>`

## Demo accounts

| Role | Email | Password |
|---------|---------------------|-----------|
| Admin | `admin@example.com` | `admin123` |
| Teacher | `tom@faculty.edu` | `teach123` |
| Student | `sara@student.edu` | `pass123` |

Pick the matching role on the login screen. Change these accounts before using the app for anything real.

## Architecture

```
Browser (frontend/)  --- HTTPS + JSON --->  Express REST API (backend/)  --->  PostgreSQL
HTML, CSS, JavaScript                       JWT auth, validation, roles        users, results
```

| Layer | Technology |
|----------|-------------------------------------------------|
| Frontend | HTML, CSS and vanilla JavaScript (no build step) |
| Backend | Node.js and Express REST API |
| Database | PostgreSQL |
| Security | bcrypt password hashing, JWT tokens, login rate limiting, role checks on every endpoint |
| Hosting | Render (app) and Neon (database) |

The backend also serves the frontend files, so the whole app is one deployable service with one URL.

## Features

- Login with role selection and 8-hour sessions
- **Dashboard**: totals, students per department, grade distribution, top students by CGPA (Admin/Teacher); personal CGPA and grades (Student)
- **Profile**: edit phone, department and birthday, upload a profile photo, change password
- **Records** (Admin): search, add, edit, delete users
- **Results** (Admin/Teacher): search, add, edit, delete results, export CSV; Students see only their own
- Validation for email, birthday, year, semester (1-8 or I-VIII), GPA (0-10) and grade
- Duplicate protection for IDs, emails and results
- Dark mode, responsive layout

## Permissions

| Action | Admin | Teacher | Student |
|---------------------------|:-----:|:-------:|:-------:|
| View all records | Yes | Yes | Own only |
| Add / edit / delete users | Yes | No | No |
| Add / edit / delete results | Yes | Yes | No |
| View results | All | All | Own only |
| Export results CSV | Yes | Yes | No |
| Dashboard | Full | Full | Personal |

## API

| Method | Endpoint | Access |
|--------|----------|--------|
| POST | `/api/auth/login` | Public |
| GET, PUT | `/api/me` | Logged in |
| POST | `/api/me/password` | Logged in |
| GET | `/api/users` | Logged in (Students: own record) |
| POST, PUT, DELETE | `/api/users`, `/api/users/:id` | Admin |
| GET | `/api/results` | Logged in (Students: own results) |
| POST, PUT, DELETE | `/api/results`, `/api/results/:id` | Admin, Teacher |
| GET | `/api/results-export` | Admin, Teacher |
| GET | `/api/stats` | Logged in |
| GET | `/healthz` | Public |

## Run locally

Requirements: Node.js 18+ and a PostgreSQL database.

```bash
git clone https://github.com/<your-username>/<your-repo>.git
cd <your-repo>
npm install
cp .env.example .env        # then edit DATABASE_URL and JWT_SECRET
npm start
```

Open http://localhost:3000. Tables are created and demo data is added automatically on first start.

## Deploy (free tier)

1. **Database:** create a project on [Neon](https://neon.tech) and copy the connection string.
2. **Push this repo to GitHub.**
3. **App:** on [Render](https://render.com) choose New > Blueprint (it reads `render.yaml`), connect the repo, and set `DATABASE_URL` to the Neon string. `JWT_SECRET` is generated for you.
4. Open the URL Render gives you, and paste it at the top of this README.

Free plans may put the app to sleep when idle, so the first request after a break can take a while to load.

## Project structure

```
.
├── backend/
│   ├── server.js     # routes, auth, role checks
│   ├── db.js         # PostgreSQL connection, schema, demo data
│   └── validate.js   # validation rules
├── frontend/
│   ├── index.html
│   ├── style.css
│   └── app.js
├── render.yaml       # Render deployment config
├── .env.example
└── package.json
```

## Database

**users**: `id` (PK), `name`, `email` (unique, case-insensitive), `password_hash`, `role`, `phone`, `department`, `birthday`, `avatar`

**results**: `id` (PK), `student_id` (FK to users, cascade delete), `exam`, `year`, `semester`, `gpa`, `grade`. One result per student, exam, year and semester.

CGPA is the average GPA across a student's results.

## Team

CSE-AIML (D), II Year, SRM Institute of Science and Technology, Ramapuram

| Name | Register No. |
|-------------------------------|-----------------|
| M. Rama Sri Phani Charan | RA2511026020217 |
| K. Sandeep Kumar Reddy | RA2511026020236 |
| P. Dimpul Ganesh | RA2511026020237 |
| S. Sai Dheeraj Reddy | RA2511026020246 |

## License

MIT, for educational use.
