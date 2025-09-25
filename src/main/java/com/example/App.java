package com.example;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.*;

public class App {

    private static Connection connection;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(App::createAndShowLogin);
    }

    private static void createAndShowLogin() {
        setDarkTheme();

        JFrame frame = new JFrame("MySQL Connector - Login");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Host
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Host:"), gbc);
        JTextField hostField = new JTextField("localhost", 15);
        gbc.gridx = 1;
        panel.add(hostField, gbc);

        // Port
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Port:"), gbc);
        JTextField portField = new JTextField("3306", 6);
        gbc.gridx = 1;
        panel.add(portField, gbc);

        // Username
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Username:"), gbc);
        JTextField userField = new JTextField("root", 12);
        gbc.gridx = 1;
        panel.add(userField, gbc);

        // Password
        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("Password:"), gbc);
        JPasswordField passField = new JPasswordField(12);
        gbc.gridx = 1;
        panel.add(passField, gbc);

        // Status area
        JTextArea statusArea = new JTextArea(6, 30);
        statusArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(statusArea);
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(scrollPane, gbc);

        // Connect button
        JButton connectBtn = new JButton("Connect");
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(connectBtn, gbc);

        connectBtn.addActionListener((ActionEvent e) -> {
            String host = hostField.getText().trim();
            String port = portField.getText().trim();
            String user = userField.getText().trim();
            String password = new String(passField.getPassword());

            String url = String.format(
                "jdbc:mysql://%s:%s/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                host, port
            );

            statusArea.append("Connecting to " + host + ":" + port + "...\n");

            try {
                connection = DriverManager.getConnection(url, user, password);
                statusArea.append("✅ Connected successfully!\n");

                frame.dispose(); // close login
                createDatabaseSelector();

            } catch (SQLException ex) {
                statusArea.append("❌ Failed to connect: " + ex.getMessage() + "\n");
            }
        });

        frame.add(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void createDatabaseSelector() {
        JFrame dbFrame = new JFrame("Select Database");
        dbFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel dbPanel = new JPanel(new GridBagLayout());
        dbPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc2 = new GridBagConstraints();
        gbc2.insets = new Insets(5, 5, 5, 5);
        gbc2.fill = GridBagConstraints.HORIZONTAL;

        // Load databases
        DefaultComboBoxModel<String> dbModel = new DefaultComboBoxModel<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW DATABASES")) {
            while (rs.next()) {
                dbModel.addElement(rs.getString(1));
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(dbFrame, "Error loading databases: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }

        gbc2.gridx = 0; gbc2.gridy = 0;
        dbPanel.add(new JLabel("Database:"), gbc2);
        JComboBox<String> dbBox = new JComboBox<>(dbModel);
        dbBox.setEditable(true);
        gbc2.gridx = 1;
        dbPanel.add(dbBox, gbc2);

        JButton selectBtn = new JButton("Use Database");
        gbc2.gridx = 0; gbc2.gridy = 1; gbc2.gridwidth = 2;
        dbPanel.add(selectBtn, gbc2);

        selectBtn.addActionListener(ev -> {
            String chosenDb = (String) dbBox.getEditor().getItem();
            try (Statement stmt2 = connection.createStatement()) {
                if (((DefaultComboBoxModel<String>) dbBox.getModel())
                        .getIndexOf(chosenDb) == -1) {
                    stmt2.executeUpdate("CREATE DATABASE " + chosenDb);
                }
                stmt2.execute("USE " + chosenDb);
                dbFrame.dispose(); // close selector
                createMainCrudWindow(chosenDb);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(dbFrame, "Error selecting database: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        dbFrame.add(dbPanel);
        dbFrame.pack();
        dbFrame.setLocationRelativeTo(null);
        dbFrame.setVisible(true);
    }

    private static void createMainCrudWindow(String database) {
        JFrame mainFrame = new JFrame("CRUD - DB: " + database);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel(new GridLayout(5, 1, 10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JButton createBtn = new JButton("Create (INSERT example)");
        JButton readBtn = new JButton("Read (SELECT example)");
        JButton updateBtn = new JButton("Update (UPDATE example)");
        JButton deleteBtn = new JButton("Delete (DELETE example)");
        JButton disconnectBtn = new JButton("Disconnect");

        JTextArea output = new JTextArea(10, 40);
        output.setEditable(false);
        JScrollPane scroll = new JScrollPane(output);

        panel.add(createBtn);
        panel.add(readBtn);
        panel.add(updateBtn);
        panel.add(deleteBtn);
        panel.add(disconnectBtn);

        createBtn.addActionListener(e -> {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS users (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50))");
                stmt.executeUpdate("INSERT INTO users (name) VALUES ('Alice')");
                output.append("✅ Inserted example row (Alice).\n");
            } catch (SQLException ex) {
                output.append("❌ Error inserting: " + ex.getMessage() + "\n");
            }
        });

        readBtn.addActionListener(e -> {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
                while (rs.next()) {
                    output.append("Row: id=" + rs.getInt("id") + ", name=" + rs.getString("name") + "\n");
                }
            } catch (SQLException ex) {
                output.append("❌ Error reading: " + ex.getMessage() + "\n");
            }
        });

        updateBtn.addActionListener(e -> {
            try (Statement stmt = connection.createStatement()) {
                int rows = stmt.executeUpdate("UPDATE users SET name='Bob' WHERE name='Alice'");
                output.append("✅ Updated " + rows + " row(s).\n");
            } catch (SQLException ex) {
                output.append("❌ Error updating: " + ex.getMessage() + "\n");
            }
        });

        deleteBtn.addActionListener(e -> {
            try (Statement stmt = connection.createStatement()) {
                int rows = stmt.executeUpdate("DELETE FROM users WHERE name='Bob'");
                output.append("✅ Deleted " + rows + " row(s).\n");
            } catch (SQLException ex) {
                output.append("❌ Error deleting: " + ex.getMessage() + "\n");
            }
        });

        disconnectBtn.addActionListener(e -> {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                    output.append("🔌 Disconnected.\n");
                }
            } catch (SQLException ex) {
                output.append("❌ Error disconnecting: " + ex.getMessage() + "\n");
            }
            mainFrame.dispose();
            createAndShowLogin();
        });

        mainFrame.add(panel, BorderLayout.NORTH);
        mainFrame.add(scroll, BorderLayout.CENTER);
        mainFrame.pack();
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setVisible(true);
    }

    private static void setDarkTheme() {
        Color bg = new Color(45, 45, 45);
        Color fg = new Color(230, 230, 230);
        UIManager.put("Panel.background", bg);
        UIManager.put("Label.foreground", fg);
        UIManager.put("TextField.background", new Color(60, 60, 60));
        UIManager.put("TextField.foreground", fg);
        UIManager.put("PasswordField.background", new Color(60, 60, 60));
        UIManager.put("PasswordField.foreground", fg);
        UIManager.put("ComboBox.background", new Color(60, 60, 60));
        UIManager.put("ComboBox.foreground", fg);
        UIManager.put("Button.background", new Color(70, 70, 70));
        UIManager.put("Button.foreground", fg);
    }
}
