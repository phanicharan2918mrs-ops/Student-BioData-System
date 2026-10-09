// Validation rules ported from the original Java version.
const ROLES = ['Admin', 'Teacher', 'Student'];
const GRADES = ['O', 'A+', 'A', 'B+', 'B', 'C', 'D', 'E', 'F', 'NA'];
const ROMAN = { I: 1, II: 2, III: 3, IV: 4, V: 5, VI: 6, VII: 7, VIII: 8 };

const clean = (s) => (s == null ? '' : String(s).replace(/[\t\r\n]/g, ' ').trim());

const isEmail = (e) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e) && e.length <= 100;

function isBirthday(s) {
  if (!s) return true;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(s)) return false;
  const d = new Date(s + 'T00:00:00Z');
  return !isNaN(d) && d.toISOString().slice(0, 10) === s && d <= new Date();
}

function normSemester(s) {
  const v = clean(s).toUpperCase();
  if (/^[1-8]$/.test(v)) return Number(v);
  return ROMAN[v] || null;
}

function validateResult(b) {
  const student_id = clean(b.student_id);
  const exam = clean(b.exam);
  const year = Number(b.year);
  const semester = normSemester(b.semester);
  const gpa = Number(b.gpa);
  const grade = clean(b.grade).toUpperCase();
  if (!student_id || !exam) return { error: 'Student ID and exam are required.' };
  if (!Number.isInteger(year) || year < 1900 || year > 2100) return { error: 'Year must be between 1900 and 2100.' };
  if (!semester) return { error: 'Semester must be 1-8 or I-VIII.' };
  if (b.gpa === '' || b.gpa == null || !Number.isFinite(gpa) || gpa < 0 || gpa > 10) return { error: 'GPA must be between 0 and 10.' };
  if (!GRADES.includes(grade)) return { error: 'Grade must be one of ' + GRADES.join(', ') + '.' };
  return { value: { student_id, exam, year, semester, gpa: Math.round(gpa * 100) / 100, grade } };
}

module.exports = { ROLES, GRADES, clean, isEmail, isBirthday, validateResult };
