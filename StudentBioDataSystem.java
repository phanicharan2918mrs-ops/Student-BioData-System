import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class StudentBioDataSystem {

    // ---------- Constants ----------
    private static final int USER_FIELDS = 9;
    private static final int RESULT_FIELDS = 6;

    private static final String[] ROLES = {"Admin", "Teacher", "Student"};

    private static final Path USERS = Paths.get("users.csv");
    private static final Path RESULTS = Paths.get("results.csv");

    // ---------- Data model ----------
    static class User {
        String id, name, email, hash, role, phone, dept, birthday, avatar;

        User(String... f) {
            if (f == null || f.length != USER_FIELDS) {
                throw new IllegalArgumentException("Invalid user record.");
            }

            id = f[0];
            name = f[1];
            email = f[2];
            hash = f[3];
            role = f[4];
            phone = f[5];
            dept = f[6];
            birthday = f[7];
            avatar = f[8];
        }

        String[] row() {
            return new String[]{
                    clean(id), clean(name), clean(email), clean(hash), clean(role),
                    clean(phone), clean(dept), clean(birthday), clean(avatar)
            };
        }
    }

    static List<User> users = new ArrayList<>();
    static List<String[]> results = new ArrayList<>();
    static User current;

    // ---------- Utility ----------
    static String sha(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));

            StringBuilder b = new StringBuilder(digest.length * 2);
            for (byte x : digest) {
                b.append(String.format(Locale.ROOT, "%02x", x & 0xff));
            }
            return b.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    static String clean(String s) {
        return s == null ? "" : s.replaceAll("[\\t\\r\\n]", " ").trim();
    }

    static String lower(String s) {
        return clean(s).toLowerCase(Locale.ROOT);
    }

    static void msg(Component parent, String s) {
        JOptionPane.showMessageDialog(parent, s);
    }

    static void msg(String s) {
        msg(null, s);
    }

    static boolean isValidRole(String role) {
        return Arrays.asList(ROLES).contains(role);
    }

    static boolean isValidEmail(String email) {
        return email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    static boolean isValidBirthday(String birthday) {
        if (birthday.isEmpty()) return true;

        try {
            LocalDate date = LocalDate.parse(birthday);
            return !date.isAfter(LocalDate.now());
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    static boolean isValidYear(String year) {
        try {
            int y = Integer.parseInt(year);
            return y >= 1900 && y <= 2100;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static boolean isValidSemester(String semester) {
        return semester.matches("(?i)([1-8]|I|II|III|IV|V|VI|VII|VIII)");
    }

    static boolean isValidGrade(String grade) {
        return grade.matches("(?i)(O|A\\+|A|B\\+|B|C|D|E|F|NA)");
    }

    // ---------- Storage ----------
    static void load() {
        users.clear();
        results.clear();

        List<String> loadErrors = new ArrayList<>();
        boolean defaultsCreated = false;

        // Load users first because result validation depends on the user list.
        try {
            if (Files.exists(USERS)) {
                List<String> lines = Files.readAllLines(USERS, StandardCharsets.UTF_8);
                int lineNo = 0;

                for (String line : lines) {
                    lineNo++;
                    if (line.trim().isEmpty()) continue;

                    String[] fields = line.split("\t", -1);
                    if (fields.length != USER_FIELDS) {
                        loadErrors.add("users.csv line " + lineNo + " has " + fields.length
                                + " fields; expected " + USER_FIELDS + ".");
                        continue;
                    }

                    try {
                        User u = new User(fields);

                        if (u.id.isEmpty() || u.email.isEmpty() || u.hash.isEmpty()
                                || !isValidRole(u.role)) {
                            loadErrors.add("users.csv line " + lineNo + " is invalid.");
                            continue;
                        }

                        if (find(u.id) != null || findByEmail(u.email) != null) {
                            loadErrors.add("Duplicate user ID/email at users.csv line " + lineNo + ".");
                            continue;
                        }

                        users.add(u);
                    } catch (RuntimeException ex) {
                        loadErrors.add("users.csv line " + lineNo + " could not be loaded.");
                    }
                }
            }
        } catch (IOException e) {
            msg("Load error: " + e.getMessage());
        }

        // First run or unusable/empty user file: create default accounts.
        if (users.isEmpty()) {
            defaultsCreated = true;
            users.add(new User(
                    "A001", "Admin", "admin@example.com", sha("admin123"),
                    "Admin", "", "CSE", "", ""
            ));
            users.add(new User(
                    "T001", "Tom Teacher", "tom@faculty.edu", sha("teach123"),
                    "Teacher", "", "CSE", "", ""
            ));
            users.add(new User(
                    "S001", "Sara Student", "sara@student.edu", sha("pass123"),
                    "Student", "", "CSE-AIML", "", ""
            ));
        }

        // Load results only after users are available for validation.
        try {
            if (Files.exists(RESULTS)) {
                List<String> lines = Files.readAllLines(RESULTS, StandardCharsets.UTF_8);
                int lineNo = 0;

                for (String line : lines) {
                    lineNo++;
                    if (line.trim().isEmpty()) continue;

                    String[] fields = line.split("\t", -1);
                    if (fields.length != RESULT_FIELDS) {
                        loadErrors.add("results.csv line " + lineNo + " has " + fields.length
                                + " fields; expected " + RESULT_FIELDS + ".");
                        continue;
                    }

                    if (!isValidResultRecord(fields)) {
                        loadErrors.add("results.csv line " + lineNo + " is invalid.");
                        continue;
                    }

                    if (hasDuplicateResult(fields, null)) {
                        loadErrors.add("Duplicate result at results.csv line " + lineNo + ".");
                        continue;
                    }

                    results.add(fields);
                }
            }
        } catch (IOException e) {
            msg("Load error: " + e.getMessage());
        }

        // Persist defaults immediately if this was the first run.
        if (defaultsCreated || !Files.exists(USERS) || !Files.exists(RESULTS)) {
            save();
        }

        if (!loadErrors.isEmpty()) {
            msg("Some invalid data was skipped while loading:\n\n"
                    + String.join("\n", loadErrors));
        }
    }

    static boolean isValidResultRecord(String[] r) {
        if (r == null || r.length != RESULT_FIELDS) return false;

        for (String value : r) {
            if (clean(value).isEmpty()) return false;
        }

        User student = find(r[0]);
        if (student == null || !"Student".equals(student.role)) return false;
        if (!isValidYear(r[2])) return false;
        if (!isValidSemester(r[3])) return false;
        if (!isValidGrade(r[5])) return false;

        try {
            double gpa = Double.parseDouble(r[4]);
            return Double.isFinite(gpa) && gpa >= 0.0 && gpa <= 10.0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static void save() {
        try {
            List<String> userLines = new ArrayList<>();
            List<String> resultLines = new ArrayList<>();

            for (User u : users) {
                userLines.add(String.join("\t", u.row()));
            }

            for (String[] r : results) {
                if (r != null && r.length == RESULT_FIELDS) {
                    resultLines.add(String.join("\t", cleanedArray(r)));
                }
            }

            Files.write(
                    USERS,
                    userLines,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );

            Files.write(
                    RESULTS,
                    resultLines,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException e) {
            msg("Save error: " + e.getMessage());
        }
    }

    static String[] cleanedArray(String[] values) {
        String[] copy = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            copy[i] = clean(values[i]);
        }
        return copy;
    }

    static User find(String id) {
        String wanted = clean(id);
        for (User u : users) {
            if (u.id.equals(wanted)) return u;
        }
        return null;
    }

    static User findByEmail(String email) {
        String wanted = lower(email);
        for (User u : users) {
            if (lower(u.email).equals(wanted)) return u;
        }
        return null;
    }

    // ---------- Entry point and login ----------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            load();
            showLogin();
        });
    }

    static void showLogin() {
        JFrame f = new JFrame("Student Bio-Data System - Login");
        JTextField email = new JTextField(20);
        JPasswordField pass = new JPasswordField(20);
        JComboBox<String> role = new JComboBox<>(ROLES);
        JButton login = new JButton("Login");

        Runnable doLogin = () -> {
            String enteredEmail = clean(email.getText());
            String password = new String(pass.getPassword());
            String selectedRole = String.valueOf(role.getSelectedItem());

            if (enteredEmail.isEmpty() || password.isEmpty()) {
                msg(f, "Please enter your email and password.");
                return;
            }

            for (User u : users) {
                if (lower(u.email).equals(lower(enteredEmail))
                        && u.hash.equals(sha(password))
                        && u.role.equals(selectedRole)) {
                    current = u;
                    pass.setText("");
                    f.dispose();
                    showMain();
                    return;
                }
            }

            pass.setText("");
            msg(f, "Invalid email, password, or role.");
        };

        login.addActionListener(e -> doLogin.run());
        pass.addActionListener(e -> doLogin.run());

        f.setLayout(new GridLayout(4, 2, 8, 8));
        f.add(new JLabel("Email:"));
        f.add(email);
        f.add(new JLabel("Password:"));
        f.add(pass);
        f.add(new JLabel("Role:"));
        f.add(role);
        f.add(new JLabel(""));
        f.add(login);

        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.pack();
        f.setResizable(false);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    // ---------- Main window ----------
    static void showMain() {
        JFrame f = new JFrame("Student Bio-Data System - " + current.role);
        JTabbedPane tabs = new JTabbedPane();

        tabs.addTab("Profile", profileTab(f));
        tabs.addTab("Records", recordsTab(f));
        tabs.addTab("Results", resultsTab(f));

        if ("Admin".equals(current.role)) {
            tabs.addTab("Settings", settingsTab(f));
        }

        tabs.addTab("About", aboutTab(f));

        f.add(tabs);
        f.setSize(900, 560);
        f.setMinimumSize(new Dimension(760, 480));
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    // ---------- Profile ----------
    static Icon loadAvatar(String path) {
        if (path == null || path.isEmpty()) return null;

        Path imagePath = Paths.get(path);
        if (!Files.isRegularFile(imagePath)) return null;

        ImageIcon source = new ImageIcon(path);
        if (source.getIconWidth() <= 0 || source.getIconHeight() <= 0) {
            return null;
        }

        Image img = source.getImage().getScaledInstance(
                150, 150, Image.SCALE_SMOOTH
        );
        return new ImageIcon(img);
    }

    static JPanel profileTab(JFrame f) {
        JPanel profile = new JPanel(new BorderLayout(10, 10));
        profile.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTextField phone = new JTextField(current.phone, 15);
        JTextField dept = new JTextField(current.dept, 15);
        JTextField bday = new JTextField(current.birthday, 15);

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("ID:"));
        form.add(new JLabel(current.id));
        form.add(new JLabel("Name:"));
        form.add(new JLabel(current.name));
        form.add(new JLabel("Email:"));
        form.add(new JLabel(current.email));
        form.add(new JLabel("Role:"));
        form.add(new JLabel(current.role));
        form.add(new JLabel("Phone:"));
        form.add(phone);
        form.add(new JLabel("Department:"));
        form.add(dept);
        form.add(new JLabel("Birthday (yyyy-mm-dd):"));
        form.add(bday);

        JButton saveBtn = new JButton("Save Changes");
        saveBtn.addActionListener(e -> {
            String newPhone = clean(phone.getText());
            String newDept = clean(dept.getText());
            String newBirthday = clean(bday.getText());

            if (!isValidBirthday(newBirthday)) {
                msg(f, "Birthday must be in yyyy-mm-dd format and cannot be in the future.");
                return;
            }

            current.phone = newPhone;
            current.dept = newDept;
            current.birthday = newBirthday;

            save();
            msg(f, "Profile saved successfully.");
        });

        form.add(new JLabel(""));
        form.add(saveBtn);

        JLabel avatar = new JLabel(loadAvatar(current.avatar), JLabel.CENTER);
        avatar.setPreferredSize(new Dimension(170, 170));
        avatar.setBorder(BorderFactory.createTitledBorder("Profile Image"));

        JButton upload = new JButton("Upload Image");
        upload.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Choose Profile Image");
            chooser.setFileFilter(
                    new FileNameExtensionFilter(
                            "Image files (*.jpg, *.jpeg, *.png)",
                            "jpg", "jpeg", "png"
                    )
            );

            if (chooser.showOpenDialog(f) == JFileChooser.APPROVE_OPTION) {
                Path selected = chooser.getSelectedFile().toPath();

                if (!Files.isRegularFile(selected)) {
                    msg(f, "Selected file is not valid.");
                    return;
                }

                ImageIcon test = new ImageIcon(selected.toString());
                if (test.getIconWidth() <= 0 || test.getIconHeight() <= 0) {
                    msg(f, "The selected file is not a valid image.");
                    return;
                }

                current.avatar = selected.toAbsolutePath().toString();
                avatar.setIcon(loadAvatar(current.avatar));
                save();
            }
        });

        JPanel right = new JPanel(new BorderLayout(5, 5));
        right.add(avatar, BorderLayout.CENTER);
        right.add(upload, BorderLayout.SOUTH);

        profile.add(form, BorderLayout.CENTER);
        profile.add(right, BorderLayout.EAST);

        return profile;
    }

    // ---------- Records ----------
    static JPanel recordsTab(JFrame f) {
        boolean admin = "Admin".equals(current.role);
        boolean student = "Student".equals(current.role);

        DefaultTableModel model = new DefaultTableModel(
                new String[]{
                        "ID", "Name", "Email", "Role",
                        "Phone", "Department", "Birthday"
                }, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);

        JTextField query = new JTextField(15);

        Runnable refresh = () -> {
            model.setRowCount(0);
            String key = lower(query.getText());

            for (User u : users) {
                if (student && u != current) continue;

                if (lower(u.id).contains(key)
                        || lower(u.name).contains(key)
                        || lower(u.email).contains(key)) {

                    model.addRow(new Object[]{
                            u.id, u.name, u.email, u.role,
                            u.phone, u.dept, u.birthday
                    });
                }
            }
        };

        refresh.run();

        JButton search = new JButton("Search");
        JButton add = new JButton("Add");
        JButton edit = new JButton("Edit");
        JButton del = new JButton("Delete");

        search.addActionListener(e -> refresh.run());

        add.addActionListener(e -> {
            if (admin && editUser(f, null)) {
                refresh.run();
            }
        });

        edit.addActionListener(e -> {
            if (!admin) return;

            int viewRow = table.getSelectedRow();
            if (viewRow < 0) {
                msg(f, "Select a user first.");
                return;
            }

            int modelRow = table.convertRowIndexToModel(viewRow);
            String id = String.valueOf(model.getValueAt(modelRow, 0));
            User selected = find(id);

            if (selected != null && editUser(f, selected)) {
                refresh.run();
            }
        });

        del.addActionListener(e -> {
            if (!admin) return;

            int viewRow = table.getSelectedRow();
            if (viewRow < 0) {
                msg(f, "Select a user first.");
                return;
            }

            int modelRow = table.convertRowIndexToModel(viewRow);
            String id = String.valueOf(model.getValueAt(modelRow, 0));
            User selected = find(id);

            if (selected == null) return;

            if (selected == current) {
                msg(f, "You cannot delete the account you are currently logged in with.");
                return;
            }

            int choice = JOptionPane.showConfirmDialog(
                    f,
                    "Delete " + selected.name + " and all of their results?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (choice == JOptionPane.YES_OPTION) {
                users.remove(selected);
                results.removeIf(r -> r.length >= 1 && r[0].equals(selected.id));
                save();
                refresh.run();
            }
        });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Search:"));
        top.add(query);
        top.add(search);

        if (admin) {
            top.add(add);
            top.add(edit);
            top.add(del);
        }

        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(table), BorderLayout.CENTER);

        return p;
    }

    static boolean editUser(JFrame f, User u) {
        boolean isNew = u == null;

        JTextField id = new JTextField(isNew ? "" : u.id);
        id.setEditable(isNew);

        JTextField name = new JTextField(isNew ? "" : u.name);
        JTextField email = new JTextField(isNew ? "" : u.email);
        JPasswordField pw = new JPasswordField();

        JComboBox<String> role = new JComboBox<>(ROLES);
        if (!isNew) role.setSelectedItem(u.role);

        JTextField phone = new JTextField(isNew ? "" : u.phone);
        JTextField dept = new JTextField(isNew ? "" : u.dept);
        JTextField bday = new JTextField(isNew ? "" : u.birthday);

        JPanel p = new JPanel(new GridLayout(0, 2, 8, 8));
        p.add(new JLabel("ID:"));
        p.add(id);
        p.add(new JLabel("Name:"));
        p.add(name);
        p.add(new JLabel("Email:"));
        p.add(email);
        p.add(new JLabel(isNew
                ? "Password:"
                : "New password (blank = keep):"));
        p.add(pw);
        p.add(new JLabel("Role:"));
        p.add(role);
        p.add(new JLabel("Phone:"));
        p.add(phone);
        p.add(new JLabel("Department:"));
        p.add(dept);
        p.add(new JLabel("Birthday:"));
        p.add(bday);

        if (!isNew && u == current) {
            role.setEnabled(false);
        }

        int option = JOptionPane.showConfirmDialog(
                f,
                p,
                isNew ? "Add User" : "Edit User",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (option != JOptionPane.OK_OPTION) return false;

        String nid = clean(id.getText());
        String nname = clean(name.getText());
        String nemail = clean(email.getText());
        String nphone = clean(phone.getText());
        String ndept = clean(dept.getText());
        String nbday = clean(bday.getText());
        String selectedRole = String.valueOf(role.getSelectedItem());

        if (nid.isEmpty() || nname.isEmpty() || nemail.isEmpty()) {
            msg(f, "ID, name, and email are required.");
            return false;
        }

        if (!isValidEmail(nemail)) {
            msg(f, "Enter a valid email address.");
            return false;
        }

        if (!isValidBirthday(nbday)) {
            msg(f, "Birthday must be in yyyy-mm-dd format and cannot be in the future.");
            return false;
        }

        if (!isValidRole(selectedRole)) {
            msg(f, "Please select a valid role.");
            return false;
        }

        char[] password = pw.getPassword();
        try {
            if (isNew && password.length == 0) {
                msg(f, "Password is required for a new user.");
                return false;
            }

            for (User x : users) {
                if (x != u && x.id.equals(nid)) {
                    msg(f, "User ID already exists.");
                    return false;
                }

                if (x != u && lower(x.email).equals(lower(nemail))) {
                    msg(f, "Email already exists.");
                    return false;
                }
            }

            if (isNew) {
                u = new User(
                        nid, nname, nemail, "",
                        selectedRole, nphone, ndept, nbday, ""
                );
                users.add(u);
            } else {
                u.name = nname;
                u.email = nemail;
                if (u != current) {
                    u.role = selectedRole;
                }
                u.phone = nphone;
                u.dept = ndept;
                u.birthday = nbday;
            }

            if (password.length > 0) {
                u.hash = sha(new String(password));
            }

            save();
            return true;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    // ---------- Results ----------
    static String[] rowAt(DefaultTableModel model, int row) {
        String[] values = new String[model.getColumnCount()];

        for (int i = 0; i < values.length; i++) {
            Object value = model.getValueAt(row, i);
            values[i] = value == null ? "" : String.valueOf(value);
        }

        return values;
    }

    static JPanel resultsTab(JFrame f) {
        boolean student = "Student".equals(current.role);

        DefaultTableModel model = new DefaultTableModel(
                new String[]{
                        "Student ID", "Exam", "Year",
                        "Semester", "GPA", "Grade"
                }, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);

        JTextField query = new JTextField(12);

        Runnable refresh = () -> {
            model.setRowCount(0);
            String key = lower(query.getText());

            for (String[] r : results) {
                if (r == null || r.length != RESULT_FIELDS) continue;

                boolean show = student
                        ? r[0].equals(current.id)
                        : lower(r[0]).contains(key);

                if (show) {
                    model.addRow(r.clone());
                }
            }
        };

        refresh.run();

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));

        if (!student) {
            JButton search = new JButton("Search");
            JButton add = new JButton("Add");
            JButton edit = new JButton("Edit");
            JButton del = new JButton("Delete");

            search.addActionListener(e -> refresh.run());

            add.addActionListener(e -> {
                if (editResult(f, null)) {
                    refresh.run();
                }
            });

            edit.addActionListener(e -> {
                int viewRow = table.getSelectedRow();

                if (viewRow < 0) {
                    msg(f, "Select a result first.");
                    return;
                }

                int modelRow = table.convertRowIndexToModel(viewRow);
                String[] old = rowAt(model, modelRow);

                if (editResult(f, old)) {
                    refresh.run();
                }
            });

            del.addActionListener(e -> {
                int viewRow = table.getSelectedRow();

                if (viewRow < 0) {
                    msg(f, "Select a result first.");
                    return;
                }

                int modelRow = table.convertRowIndexToModel(viewRow);
                String[] old = rowAt(model, modelRow);

                int choice = JOptionPane.showConfirmDialog(
                        f,
                        "Delete this result?",
                        "Confirm Delete",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );

                if (choice == JOptionPane.YES_OPTION) {
                    removeOneResult(old);
                    save();
                    refresh.run();
                }
            });

            top.add(new JLabel("Student ID:"));
            top.add(query);
            top.add(search);
            top.add(add);
            top.add(edit);
            top.add(del);
        } else {
            top.add(new JLabel("Students can view their own results only."));
        }

        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(table), BorderLayout.CENTER);

        return p;
    }

    static void removeOneResult(String[] target) {
        for (int i = 0; i < results.size(); i++) {
            if (Arrays.equals(results.get(i), target)) {
                results.remove(i);
                return;
            }
        }
    }

    static boolean editResult(JFrame f, String[] old) {
        String[] labels = {
                "Student ID", "Exam", "Year",
                "Semester", "GPA (0-10)", "Grade"
        };

        JTextField[] fields = new JTextField[RESULT_FIELDS];
        JPanel p = new JPanel(new GridLayout(0, 2, 8, 8));

        for (int i = 0; i < RESULT_FIELDS; i++) {
            fields[i] = new JTextField(
                    old == null ? "" : old[i],
                    14
            );
            p.add(new JLabel(labels[i] + ":"));
            p.add(fields[i]);
        }

        int option = JOptionPane.showConfirmDialog(
                f,
                p,
                old == null ? "Add Result" : "Edit Result",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (option != JOptionPane.OK_OPTION) return false;

        String[] value = new String[RESULT_FIELDS];

        for (int i = 0; i < RESULT_FIELDS; i++) {
            value[i] = clean(fields[i].getText());
        }

        if (!isValidResultRecord(value)) {
            msg(f,
                    "Invalid result.\n\n"
                    + "• Student ID must belong to an existing Student.\n"
                    + "• Exam and all fields are required.\n"
                    + "• Year must be between 1900 and 2100.\n"
                    + "• Semester must be 1-8 or I-VIII.\n"
                    + "• GPA must be between 0 and 10.\n"
                    + "• Grade must be O, A+, A, B+, B, C, D, E, F, or NA."
            );
            return false;
        }

        if (hasDuplicateResult(value, old)) {
            msg(f, "A result for this student, exam, year, and semester already exists.");
            return false;
        }

        if (old == null) {
            results.add(value);
        } else {
            for (int i = 0; i < results.size(); i++) {
                if (Arrays.equals(results.get(i), old)) {
                    results.set(i, value);
                    break;
                }
            }
        }

        save();
        return true;
    }

    static boolean hasDuplicateResult(String[] candidate, String[] old) {
        for (String[] existing : results) {
            if (existing == null || existing.length != RESULT_FIELDS) continue;
            if (old != null && Arrays.equals(existing, old)) continue;

            boolean sameKey =
                    existing[0].equals(candidate[0])
                            && lower(existing[1]).equals(lower(candidate[1]))
                            && existing[2].equals(candidate[2])
                            && lower(existing[3]).equals(lower(candidate[3]));

            if (sameKey) return true;
        }

        return false;
    }

    // ---------- Settings ----------
    static boolean nimbus = false;

    static JPanel settingsTab(JFrame f) {
        JPanel settings = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton themeBtn = new JButton("Toggle Theme");

        themeBtn.addActionListener(e -> toggleTheme(f));
        settings.add(themeBtn);

        return settings;
    }

    static void toggleTheme(JFrame f) {
        try {
            String laf;

            if (nimbus) {
                laf = UIManager.getSystemLookAndFeelClassName();
            } else {
                laf = "javax.swing.plaf.nimbus.NimbusLookAndFeel";
            }

            UIManager.setLookAndFeel(laf);
            nimbus = !nimbus;

            SwingUtilities.updateComponentTreeUI(f);
            f.pack();
            f.setSize(900, 560);
        } catch (Exception ex) {
            msg(f, "Theme error: " + ex.getMessage());
        }
    }

    // ---------- About ----------
    static JScrollPane aboutTab(JFrame f) {
        JTextArea about = new JTextArea(
                "STUDENT BIO-DATA SYSTEM\n\n"
                        + "Developers: Phani Charan, Sandeep, Dimpul, Sai Dheeraj\n"
                        + "Version: 2.0\n"
                        + "Language: Java (Swing)\n"
                        + "Features: Login, Profile, Records, Results, "
                        + "Settings (Admin), Image Upload\n"
                        + "This project is for educational purposes."
        );

        about.setEditable(false);
        about.setLineWrap(true);
        about.setWrapStyleWord(true);
        about.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        return new JScrollPane(about);
    }
}
