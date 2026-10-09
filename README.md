# Student Bio-Data System

A desktop application built with **Core Java and Swing** for managing student bio-data and exam results. It supports role-based login (Admin, Teacher, Student), profile editing with image upload, searchable records and results, and SHA-256 password hashing. Data is stored in simple tab-separated files, so no database or internet connection is needed.

> Mini project by CSE-AIML (D), II Year, SRM Institute of Science and Technology, Ramapuram.

---

## Table of Contents

- [Features](#features)
- [User Roles](#user-roles)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [Default Login Accounts](#default-login-accounts)
- [Project Structure](#project-structure)
- [Data Storage](#data-storage)
- [Validation Rules](#validation-rules)
- [Security](#security)
- [Screenshots](#screenshots)
- [Current Status and Limitations](#current-status-and-limitations)
- [Future Plans](#future-plans)
- [Planned Database Design](#planned-database-design)
- [Team](#team)
- [License](#license)

---

## Features

- **Secure login** with email, password and role selection
- **Password hashing** using SHA-256, so passwords are never stored as plain text
- **Profile tab** to edit phone, department and birthday, and to upload a profile image (JPG, JPEG, PNG)
- **Records tab** to search, add, edit and delete users (Admin), with sortable tables
- **Results tab** to search, add, edit and delete exam results (Admin and Teacher)
- **Settings tab** (Admin only) to toggle between the System and Nimbus look and feel
- **About tab** with developer and version information
- **Input validation** for email, birthday, year, semester, GPA and grade
- **Duplicate protection** for user IDs, emails and result entries
- **Safe loading**: invalid rows in the data files are skipped and reported instead of crashing the app
- **First-run setup**: default accounts are created automatically

## User Roles

| Role | Profile | Records | Results | Settings |
|---------|---------|-------------------------|-------------------------|----------|
| Admin | Edit own | View, add, edit, delete | Add, edit, delete | Yes |
| Teacher | Edit own | View only | Add, edit, delete | No |
| Student | Edit own | View own record only | View own results only | No |

## Tech Stack

| Area | Technology |
|-----------------|-----------------------------------------------------------|
| Language | Core Java (JDK 8 or higher) |
| GUI | Java Swing (`JFrame`, `JTabbedPane`, `JTable`, `JFileChooser`) |
| Storage | Tab-separated files via `java.nio.file` |
| Security | SHA-256 via `java.security.MessageDigest` |
| Look and Feel | Nimbus and System |

## Getting Started

### Prerequisites

- JDK 8 or later ([download](https://adoptium.net/))
- Check your install: `java -version` and `javac -version`

### Run from the command line

```bash
# 1. Clone the repository
git clone https://github.com/<your-username>/<your-repo-name>.git
cd <your-repo-name>

# 2. Compile
javac StudentBioDataSystem.java

# 3. Run
java StudentBioDataSystem
```

### Run from an IDE

Open the project in Eclipse, IntelliJ IDEA, NetBeans or VS Code (with the Java extension), then run `StudentBioDataSystem.java`.

> Run the program from the same folder each time. `users.csv` and `results.csv` are created in the working directory.

## Default Login Accounts

Created automatically on first run:

| Role | Email | Password |
|---------|---------------------|------------|
| Admin | `admin@example.com` | `admin123` |
| Teacher | `tom@faculty.edu` | `teach123` |
| Student | `sara@student.edu` | `pass123` |

Select the matching role in the login screen. **Change or delete these accounts before any real use.**

## Project Structure

```
.
├── StudentBioDataSystem.java   # Entire application (UI + logic + storage)
├── users.csv                   # Generated on first run
├── results.csv                 # Generated on first run
└── README.md
```

The code in `StudentBioDataSystem.java` is organised into sections:

| Section | Purpose |
|--------------------|-----------------------------------------------------------|
| Constants | Field counts, roles and file paths |
| Data model | `User` class and in-memory lists |
| Utility | Hashing, text cleaning and validation helpers |
| Storage | `load()` and `save()` for the data files |
| Entry point, login | `main()` and `showLogin()` |
| Main window | Tabbed window built in `showMain()` |
| Profile | Profile form and image upload |
| Records | User table and the add/edit dialog |
| Results | Results table and the add/edit dialog |
| Settings, About | Theme toggle and developer info |

## Data Storage

Both files are plain text with **tab-separated** columns.

**users.csv** (9 fields)

```
id    name    email    passwordHash    role    phone    department    birthday    avatarPath
```

**results.csv** (6 fields)

```
studentId    exam    year    semester    gpa    grade
```

Lines that are blank, have the wrong number of fields, or fail validation are skipped when loading, and a summary of the skipped lines is shown.

## Validation Rules

| Field | Rule |
|----------|----------------------------------------------------------------|
| Email | Must look like `name@domain.ext` and be unique |
| User ID | Required and unique |
| Birthday | `yyyy-mm-dd`, optional, cannot be in the future |
| Role | Admin, Teacher or Student |
| Year | 1900 to 2100 |
| Semester | 1 to 8, or I to VIII |
| GPA | 0 to 10 |
| Grade | O, A+, A, B+, B, C, D, E, F or NA |
| Result | Student ID must belong to an existing Student; one result per student, exam, year and semester |

## Security

- Passwords are hashed with SHA-256 before saving.
- Access is role-based: Students see only their own data.
- An admin cannot delete the account they are logged in with.
- Deleting a user also removes that user's results.

Note: the hashes are not salted, and the data files are not encrypted. This is fine for a learning project but not for production use.

## Screenshots

Add your screenshots to a `screenshots/` folder and update the paths below.

| Login | Admin Profile |
|-------|---------------|
| ![Login](screenshots/login.png) | ![Admin Profile](screenshots/admin-profile.png) |

## Current Status and Limitations

**Implemented**

- Login with Admin, Teacher and Student roles
- SHA-256 password hashing
- Profile editing and image upload
- Records and Results management
- Data saved to files
- Admin-only settings and theme toggle

**Limitations**

- Data is stored in text files, not a database
- Profile images are referenced by their file path, so moving the image breaks the link
- Webcam capture, a clock and image-based themes appear only in the design mockups

## Future Plans

- Database support with JDBC (MySQL or SQLite)
- Webcam image capture
- File encryption, backup and recovery
- Better dark theme and image themes
- Online connectivity
- Printing option

## Planned Database Design

The current version uses files, but the database schema is already designed.

**Users**

| Column | Type | Constraints | Description |
|------------|-------------------------------------|------------------------------|------------------------|
| user_id | INT | PRIMARY KEY, AUTO_INCREMENT | Unique ID for each user |
| name | VARCHAR(100) | NOT NULL | Full name |
| email | VARCHAR(100) | UNIQUE, NOT NULL | Login email |
| password | VARCHAR(100) | NOT NULL | Password (hash) |
| user_type | ENUM('Admin','Student','Teacher') | NOT NULL | Type of user |
| phone | VARCHAR(15) | NULLABLE | Contact number |
| department | VARCHAR(50) | NULLABLE | Department name |
| student_id | VARCHAR(20) | NULLABLE | Student ID |
| birthday | DATE | NULLABLE | Date of birth |
| avatar | VARCHAR(255) | NULLABLE | Path of profile image |

**Settings** (Admin only)

| Column | Type | Constraints | Description |
|------------|-----------|-------------------------------|---------------------------|
| setting_id | INT | PRIMARY KEY, AUTO_INCREMENT | Unique setting ID |
| theme | VARCHAR(20) | DEFAULT 'Light' | Theme preference |
| updated_by | INT | FOREIGN KEY (Users.user_id) | Admin who changed setting |
| updated_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Last update time |

## Team

CSE-AIML (D), II Year, SRM Institute of Science and Technology, Ramapuram

| Name | Register No. |
|-------------------------------|-------------------|
| M. Rama Sri Phani Charan | RA2511026020217 |
| K. Sandeep Kumar Reddy | RA2511026020236 |
| P. Dimpul Ganesh | RA2511026020237 |
| S. Sai Dheeraj Reddy | RA2511026020246 |

## License

This project is for educational purposes. Add a license file (for example MIT) if you plan to share or reuse it publicly.
