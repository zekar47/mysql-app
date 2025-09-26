package com.example;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.Vector;

public class App {

    private static Connection connection;
    private static String currentDatabase;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(App::createAndShowLogin);
    }

    /* ---------- LOGIN ---------- */
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

        JTextField hostField = new JTextField("localhost", 15);
        JTextField portField = new JTextField("3306", 6);
        JTextField userField = new JTextField("root", 12);
        JPasswordField passField = new JPasswordField(12);

        gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel("Host:"), gbc);
        gbc.gridx = 1; panel.add(hostField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; panel.add(new JLabel("Port:"), gbc);
        gbc.gridx = 1; panel.add(portField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; panel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1; panel.add(userField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; panel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1; panel.add(passField, gbc);

        JButton connectBtn = new JButton("Connect");
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(connectBtn, gbc);

        connectBtn.addActionListener(e -> {
            String host = hostField.getText().trim();
            String port = portField.getText().trim();
            String user = userField.getText().trim();
            String password = new String(passField.getPassword());

            String url = String.format(
                "jdbc:mysql://%s:%s/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                host, port
            );

            try {
                connection = DriverManager.getConnection(url, user, password);
                frame.dispose();
                createDatabaseSelector();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(frame, "Connection failed: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        frame.add(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    /* ---------- DATABASE SELECTOR ---------- */
    private static void createDatabaseSelector() {
        JFrame dbFrame = new JFrame("Select Database");
        dbFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel dbPanel = new JPanel(new GridBagLayout());
        dbPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc2 = new GridBagConstraints();
        gbc2.insets = new Insets(5, 5, 5, 5);
        gbc2.fill = GridBagConstraints.HORIZONTAL;

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

        JComboBox<String> dbBox = new JComboBox<>(dbModel);
        dbBox.setEditable(true);
        gbc2.gridx = 0; gbc2.gridy = 0; dbPanel.add(new JLabel("Database:"), gbc2);
        gbc2.gridx = 1; dbPanel.add(dbBox, gbc2);

        JButton selectBtn = new JButton("Use Database");
        gbc2.gridx = 0; gbc2.gridy = 1; gbc2.gridwidth = 2;
        dbPanel.add(selectBtn, gbc2);

        selectBtn.addActionListener(ev -> {
            String chosenDb = (String) dbBox.getEditor().getItem();
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE DATABASE IF NOT EXISTS " + chosenDb);
                stmt.execute("USE " + chosenDb);
                currentDatabase = chosenDb;
                dbFrame.dispose();
                createMainCrudWindow();
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

    /* ---------- MAIN CRUD WINDOW ---------- */
    private static void createMainCrudWindow() {
        JFrame mainFrame = new JFrame("CRUD Editor - DB: " + currentDatabase);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(800, 600);

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JComboBox<String> tableBox = new JComboBox<>();
        JButton refreshBtn = new JButton("Refresh Tables");
        JButton disconnectBtn = new JButton("Disconnect");

        topPanel.add(new JLabel("Table:"));
        topPanel.add(tableBox);
        topPanel.add(refreshBtn);
        topPanel.add(disconnectBtn);

        JTable table = new JTable();
        JScrollPane scrollPane = new JScrollPane(table);

        JPanel bottomPanel = new JPanel(new FlowLayout());
        JButton insertBtn = new JButton("Insert Row");
        JButton updateBtn = new JButton("Update Row");
        JButton deleteBtn = new JButton("Delete Row");
        JButton queryBtn = new JButton("Run SQL");

        bottomPanel.add(insertBtn);
        bottomPanel.add(updateBtn);
        bottomPanel.add(deleteBtn);
        bottomPanel.add(queryBtn);

        mainFrame.add(topPanel, BorderLayout.NORTH);
        mainFrame.add(scrollPane, BorderLayout.CENTER);
        mainFrame.add(bottomPanel, BorderLayout.SOUTH);

        /* Load tables into ComboBox */
        Runnable loadTables = () -> {
            tableBox.removeAllItems();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
                while (rs.next()) {
                    tableBox.addItem(rs.getString(1));
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(mainFrame, "Error loading tables: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        };
        loadTables.run();

        /* Load selected table contents */
        tableBox.addActionListener(e -> {
            String selected = (String) tableBox.getSelectedItem();
            if (selected != null) loadTableData(selected, table);
        });
        refreshBtn.addActionListener(e -> loadTables.run());

        /* CRUD Buttons */
        insertBtn.addActionListener(e -> insertRow((String) tableBox.getSelectedItem(), mainFrame));
        updateBtn.addActionListener(e -> updateRow((String) tableBox.getSelectedItem(), mainFrame));
        deleteBtn.addActionListener(e -> deleteRow((String) tableBox.getSelectedItem(), mainFrame));
        queryBtn.addActionListener(e -> runCustomQuery(mainFrame, table));

        disconnectBtn.addActionListener(e -> {
            try {
                if (connection != null && !connection.isClosed()) connection.close();
            } catch (SQLException ignored) {}
            mainFrame.dispose();
            createAndShowLogin();
        });

        mainFrame.setVisible(true);
    }

    /* ---------- HELPERS ---------- */
    private static void loadTableData(String tableName, JTable table) {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName)) {
            DefaultTableModel model = buildTableModel(rs);
            table.setModel(model);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(table, "Error loading table: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static DefaultTableModel buildTableModel(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        Vector<String> cols = new Vector<>();
        for (int i = 1; i <= colCount; i++) cols.add(meta.getColumnName(i));

        Vector<Vector<Object>> data = new Vector<>();
        while (rs.next()) {
            Vector<Object> row = new Vector<>();
            for (int i = 1; i <= colCount; i++) row.add(rs.getObject(i));
            data.add(row);
        }
        return new DefaultTableModel(data, cols);
    }

    private static void insertRow(String tableName, JFrame parent) {
        if (tableName == null) return;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName + " LIMIT 1")) {
            ResultSetMetaData meta = rs.getMetaData();
            JPanel panel = new JPanel(new GridLayout(meta.getColumnCount(), 2));
            JTextField[] fields = new JTextField[meta.getColumnCount()];
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                panel.add(new JLabel(meta.getColumnName(i)));
                fields[i-1] = new JTextField();
                panel.add(fields[i-1]);
            }
            int res = JOptionPane.showConfirmDialog(parent, panel, "Insert Row",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (res == JOptionPane.OK_OPTION) {
                StringBuilder sql = new StringBuilder("INSERT INTO " + tableName + " VALUES (");
                for (int i = 0; i < fields.length; i++) {
                    sql.append("'").append(fields[i].getText()).append("'");
                    if (i < fields.length - 1) sql.append(", ");
                }
                sql.append(")");
                stmt.executeUpdate(sql.toString());
                JOptionPane.showMessageDialog(parent, "Row inserted!");
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(parent, "Insert failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void updateRow(String tableName, JFrame parent) {
        String sql = JOptionPane.showInputDialog(parent, "Enter UPDATE SQL for " + tableName,
                "UPDATE " + tableName + " SET column=value WHERE condition");
        if (sql != null) {
            try (Statement stmt = connection.createStatement()) {
                int rows = stmt.executeUpdate(sql);
                JOptionPane.showMessageDialog(parent, "Updated " + rows + " row(s).");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(parent, "Update failed: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static void deleteRow(String tableName, JFrame parent) {
        String sql = JOptionPane.showInputDialog(parent, "Enter DELETE SQL for " + tableName,
                "DELETE FROM " + tableName + " WHERE condition");
        if (sql != null) {
            try (Statement stmt = connection.createStatement()) {
                int rows = stmt.executeUpdate(sql);
                JOptionPane.showMessageDialog(parent, "Deleted " + rows + " row(s).");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(parent, "Delete failed: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static void runCustomQuery(JFrame parent, JTable table) {
        String sql = JOptionPane.showInputDialog(parent, "Enter SQL Query");
        if (sql != null) {
            try (Statement stmt = connection.createStatement()) {
                if (sql.trim().toLowerCase().startsWith("select")) {
                    ResultSet rs = stmt.executeQuery(sql);
                    table.setModel(buildTableModel(rs));
                } else {
                    int rows = stmt.executeUpdate(sql);
                    JOptionPane.showMessageDialog(parent, "Query executed, " + rows + " row(s) affected.");
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(parent, "Query failed: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
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
