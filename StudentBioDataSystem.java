import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;

public class StudentBioDataSystem {

    // ---------------- USER MODEL ----------------
    static class User {
        String name;
        String email;
        String password;
        String role;
        ImageIcon avatar;

        User(String name, String email, String password, String role) {
            this.name = name;
            this.email = email;
            this.password = password;
            this.role = role;
        }
    }

    // ---------------- DEMO USERS ----------------
    static User[] users = {
        new User("Admin", "admin@example.com", "admin123", "Admin"),
        new User("Sara Student", "sara@student.edu", "pass123", "Student"),
        new User("Tom Teacher", "tom@faculty.edu", "teach123", "Teacher")
    };

    static User currentUser;
    static boolean alternateTheme = false;

    // ---------------- MAIN ----------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(StudentBioDataSystem::showLogin);
    }

    // ---------------- LOGIN WINDOW ----------------
    static void showLogin() {

        JFrame frame = new JFrame("Student Bio-Data System - Login");

        JLabel emailLabel = new JLabel("Email:");
        JLabel passwordLabel = new JLabel("Password:");
        JLabel roleLabel = new JLabel("Role:");

        JTextField emailField = new JTextField(20);
        JPasswordField passwordField = new JPasswordField(20);

        JComboBox<String> roleBox =
                new JComboBox<>(new String[]{
                        "Admin",
                        "Student",
                        "Teacher"
                });

        JButton loginButton = new JButton("Login");

        loginButton.addActionListener(e -> {

            String email = emailField.getText().trim();
            String password =
                    new String(passwordField.getPassword());

            String role =
                    (String) roleBox.getSelectedItem();

            for (User user : users) {

                if (user.email.equals(email)
                        && user.password.equals(password)
                        && user.role.equals(role)) {

                    currentUser = user;

                    frame.dispose();

                    showMainWindow();

                    return;
                }
            }

            JOptionPane.showMessageDialog(
                    frame,
                    "Invalid email, password or role!",
                    "Login Error",
                    JOptionPane.ERROR_MESSAGE
            );
        });

        JPanel panel = new JPanel(new GridLayout(4, 2, 10, 10));

        panel.setBorder(
                BorderFactory.createEmptyBorder(
                        15, 15, 15, 15
                )
        );

        panel.add(emailLabel);
        panel.add(emailField);

        panel.add(passwordLabel);
        panel.add(passwordField);

        panel.add(roleLabel);
        panel.add(roleBox);

        panel.add(new JLabel(""));
        panel.add(loginButton);

        frame.add(panel);

        frame.setDefaultCloseOperation(
                JFrame.EXIT_ON_CLOSE
        );

        frame.pack();

        frame.setLocationRelativeTo(null);

        frame.setResizable(false);

        frame.setVisible(true);
    }

    // ---------------- MAIN WINDOW ----------------
    static void showMainWindow() {

        JFrame frame = new JFrame(
                "Student Bio-Data System - "
                        + currentUser.role
        );

        JTabbedPane tabs = new JTabbedPane();

        // ---------------- PROFILE ----------------

        JPanel profilePanel =
                new JPanel(new BorderLayout(10, 10));

        JTextArea profileInfo =
                new JTextArea();

        profileInfo.setEditable(false);

        profileInfo.setFont(
                new Font("Arial", Font.PLAIN, 15)
        );

        profileInfo.setText(
                "STUDENT BIO-DATA SYSTEM\n\n"
                        + "Name: "
                        + currentUser.name
                        + "\n\n"
                        + "Email: "
                        + currentUser.email
                        + "\n\n"
                        + "Role: "
                        + currentUser.role
        );

        JLabel imageLabel =
                new JLabel(
                        "No Image",
                        SwingConstants.CENTER
                );

        imageLabel.setPreferredSize(
                new Dimension(180, 180)
        );

        JButton uploadButton =
                new JButton("Upload Image");

        uploadButton.addActionListener(e -> {

            JFileChooser chooser =
                    new JFileChooser();

            chooser.setFileFilter(
                    new FileNameExtensionFilter(
                            "Image Files",
                            "jpg",
                            "jpeg",
                            "png"
                    )
            );

            int result =
                    chooser.showOpenDialog(frame);

            if (result ==
                    JFileChooser.APPROVE_OPTION) {

                String imagePath =
                        chooser.getSelectedFile()
                                .getAbsolutePath();

                currentUser.avatar =
                        new ImageIcon(imagePath);

                Image image =
                        currentUser.avatar
                                .getImage()
                                .getScaledInstance(
                                        160,
                                        160,
                                        Image.SCALE_SMOOTH
                                );

                imageLabel.setIcon(
                        new ImageIcon(image)
                );

                imageLabel.setText("");
            }
        });

        JPanel imagePanel =
                new JPanel(new BorderLayout());

        imagePanel.add(
                imageLabel,
                BorderLayout.CENTER
        );

        imagePanel.add(
                uploadButton,
                BorderLayout.SOUTH
        );

        profilePanel.add(
                new JScrollPane(profileInfo),
                BorderLayout.CENTER
        );

        profilePanel.add(
                imagePanel,
                BorderLayout.EAST
        );

        tabs.addTab(
                "Profile",
                profilePanel
        );

        // ---------------- ADMIN SETTINGS ----------------

        if (currentUser.role.equals("Admin")) {

            JPanel settingsPanel =
                    new JPanel();

            JLabel settingsLabel =
                    new JLabel(
                            "Admin Settings"
                    );

            JButton themeButton =
                    new JButton("Toggle Theme");

            themeButton.addActionListener(
                    e -> toggleTheme(frame)
            );

            settingsPanel.add(settingsLabel);

            settingsPanel.add(themeButton);

            tabs.addTab(
                    "Settings",
                    settingsPanel
            );
        }

        // ---------------- ABOUT ----------------

        JTextArea aboutText =
                new JTextArea();

        aboutText.setEditable(false);

        aboutText.setFont(
                new Font("Arial", Font.PLAIN, 14)
        );

        aboutText.setText(
                "STUDENT BIO-DATA SYSTEM\n\n"
                        + "Developed by:\n\n"
                        + "M. Rama Sri Phani Charan\n"
                        + "K. Sandeep Kumar Reddy\n"
                        + "P. Dimpul Ganesh\n"
                        + "S. Sai Dheeraj Reddy\n\n"
                        + "Version: 1.0\n"
                        + "Technology: Java Swing\n\n"
                        + "Features:\n"
                        + "- Role-based Login\n"
                        + "- Student Profile\n"
                        + "- Profile Image Upload\n"
                        + "- Admin Settings\n"
                        + "- Theme Toggle\n"
                        + "- About Section\n\n"
                        + "This project is developed "
                        + "for educational purposes."
        );

        tabs.addTab(
                "About",
                new JScrollPane(aboutText)
        );

        // ---------------- FRAME ----------------

        frame.add(tabs);

        frame.setDefaultCloseOperation(
                JFrame.EXIT_ON_CLOSE
        );

        frame.setSize(700, 450);

        frame.setLocationRelativeTo(null);

        frame.setVisible(true);
    }

    // ---------------- THEME ----------------

    static void toggleTheme(JFrame frame) {

        try {

            if (!alternateTheme) {

                UIManager.setLookAndFeel(
                        "javax.swing.plaf.nimbus.NimbusLookAndFeel"
                );

            } else {

                UIManager.setLookAndFeel(
                        UIManager
                                .getSystemLookAndFeelClassName()
                );
            }

            alternateTheme = !alternateTheme;

            SwingUtilities.updateComponentTreeUI(
                    frame
            );

        } catch (Exception e) {

            JOptionPane.showMessageDialog(
                    frame,
                    "Unable to change theme.",
                    "Theme Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }
}
