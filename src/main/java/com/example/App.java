package com.example;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.*;
import java.util.List;

public class App {

    private static Connection connection;
    private static String currentDatabase;
    private static JFrame mainFrame;
    private static JComboBox<String> tableBox;
    private static DatabaseTableModel currentModel;
    private static JTable tableView;
    private static Map<String, TableMetadata> tableMetaCache = new HashMap<>();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(App::createAndShowLogin);
    }

    /* -------------------- Login -------------------- */
    private static void createAndShowLogin() {
        setDarkTheme();
        JFrame frame = new JFrame("MySQL Connector - Login");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
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

        JTextArea status = new JTextArea(4, 30);
        status.setEditable(false);
        JScrollPane sp = new JScrollPane(status);
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.BOTH;
        panel.add(sp, gbc);

        JButton connectBtn = new JButton("Connect");
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(connectBtn, gbc);

        connectBtn.addActionListener(e -> {
            String host = hostField.getText().trim();
            String port = portField.getText().trim();
            String user = userField.getText().trim();
            String password = new String(passField.getPassword());

            String url = String.format("jdbc:mysql://%s:%s/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", host, port);
            status.append("Connecting...\n");
            try {
                connection = DriverManager.getConnection(url, user, password);
                status.append("Connected ✅\n");
                frame.dispose();
                createDatabaseSelector();
            } catch (SQLException ex) {
                status.append("Connection failed: " + ex.getMessage() + "\n");
                JOptionPane.showMessageDialog(frame, "Connection failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        frame.add(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    /* -------------------- Database Selector -------------------- */
    private static void createDatabaseSelector() {
        JFrame dbFrame = new JFrame("Select Database");
        dbFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new EmptyBorder(10,10,10,10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6,6,6,6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery("SHOW DATABASES")) {
            while (rs.next()) model.addElement(rs.getString(1));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(dbFrame, "Could not list databases: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }

        JComboBox<String> dbBox = new JComboBox<>(model);
        dbBox.setEditable(true);
        gbc.gridx = 0; gbc.gridy = 0; p.add(new JLabel("Database:"), gbc);
        gbc.gridx = 1; p.add(dbBox, gbc);

        JButton useBtn = new JButton("Use Database");
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; p.add(useBtn, gbc);

        useBtn.addActionListener(ev -> {
            String chosen = (String) dbBox.getEditor().getItem();
            try (Statement s = connection.createStatement()) {
                s.execute("CREATE DATABASE IF NOT EXISTS " + quoteIdentifier(chosen));
                s.execute("USE " + quoteIdentifier(chosen));
                currentDatabase = chosen;
                dbFrame.dispose();
                createMainEditor();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(dbFrame, "Error selecting DB: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        dbFrame.add(p);
        dbFrame.pack();
        dbFrame.setLocationRelativeTo(null);
        dbFrame.setVisible(true);
    }

    /* -------------------- Main Editor -------------------- */
    private static void createMainEditor() {
        tableMetaCache.clear();
        mainFrame = new JFrame("Mini Workbench - DB: " + currentDatabase);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(1000, 700);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        tableBox = new JComboBox<>();
        JButton refreshBtn = new JButton("Refresh Tables");
        JButton applyBtn = new JButton("Apply Changes");
        JButton runSqlBtn = new JButton("Run SQL");
        JButton disconnectBtn = new JButton("Disconnect");
        JButton addRowBtn = new JButton("Add Row");
        JButton deleteRowBtn = new JButton("Delete Selected");

        top.add(new JLabel("Table:"));
        top.add(tableBox);
        top.add(refreshBtn);
        top.add(addRowBtn);
        top.add(deleteRowBtn);
        top.add(applyBtn);
        top.add(runSqlBtn);
        top.add(disconnectBtn);

        // Table
        tableView = new JTable();
        tableView.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tableView.setRowSelectionAllowed(true);

        // Custom header tooltips support
        JTableHeader header = tableView.getTableHeader();
        header.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) {
                int col = header.columnAtPoint(e.getPoint());
                if (currentModel != null && col >= 0) {
                    String tip = currentModel.getColumnTooltip(col);
                    header.setToolTipText(tip);
                } else {
                    header.setToolTipText(null);
                }
            }
        });

        // Scroll pane
        JScrollPane centerScroll = new JScrollPane(tableView);
        centerScroll.setPreferredSize(new Dimension(900, 500));

        // Bottom area - messages
        JTextArea messages = new JTextArea(6, 80);
        messages.setEditable(false);
        JScrollPane msgScroll = new JScrollPane(messages);

        // Layout
        mainFrame.setLayout(new BorderLayout());
        mainFrame.add(top, BorderLayout.NORTH);
        mainFrame.add(centerScroll, BorderLayout.CENTER);
        mainFrame.add(msgScroll, BorderLayout.SOUTH);

        // Load tables
        Runnable loadTables = () -> {
            String selected = (String) tableBox.getSelectedItem();
            tableBox.removeAllItems();
            try (Statement s = connection.createStatement();
                 ResultSet rs = s.executeQuery("SHOW TABLES")) {
                while (rs.next()) tableBox.addItem(rs.getString(1));
                if (selected != null) tableBox.setSelectedItem(selected);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(mainFrame, "Error loading tables: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        };
        loadTables.run();

        // Table selection change with check for pending edits - FIXED: Only show when real changes exist
        tableBox.addActionListener(e -> {
            String newTable = (String) tableBox.getSelectedItem();
            if (newTable == null) return;
            if (currentModel != null && currentModel.hasRealChanges()) { // CHANGED: Check for real changes
                int opt = JOptionPane.showOptionDialog(mainFrame,
                        "You have unapplied changes. Apply them, discard or cancel?",
                        "Unapplied Changes",
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null, new Object[] {"Apply", "Discard", "Cancel"}, "Apply");
                if (opt == JOptionPane.CANCEL_OPTION || opt == JOptionPane.CLOSED_OPTION) {
                    tableBox.removeActionListener(thisListener());
                    try { tableBox.setSelectedItem(currentModel.getTableName()); } finally { tableBox.addActionListener(thisListener()); }
                    return;
                } else if (opt == JOptionPane.YES_OPTION) {
                    boolean ok = currentModel.applyChanges(messages);
                    if (!ok) {
                        tableBox.removeActionListener(thisListener());
                        try { tableBox.setSelectedItem(currentModel.getTableName()); } finally { tableBox.addActionListener(thisListener()); }
                        return;
                    }
                } else {
                    currentModel.discardChanges();
                }
            }
            loadAndShowTable(newTable, messages);
        });

        refreshBtn.addActionListener(e -> loadTables.run());

        // Add row button
        addRowBtn.addActionListener(e -> {
            if (currentModel != null) {
                currentModel.addNewRow();
            }
        });

        // Delete row button
        deleteRowBtn.addActionListener(e -> {
            if (currentModel != null) {
                int[] selectedRows = tableView.getSelectedRows();
                if (selectedRows.length == 0) {
                    JOptionPane.showMessageDialog(mainFrame, "Please select rows to delete.", "Info", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                currentModel.markRowsForDeletion(selectedRows);
            }
        });

        // Apply changes button
        applyBtn.addActionListener(e -> {
            if (currentModel == null) {
                JOptionPane.showMessageDialog(mainFrame, "No table selected.", "Info", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            boolean ok = currentModel.applyChanges(messages);
            if (ok) loadAndShowTable(currentModel.getTableName(), messages);
        });

        // Run SQL
        runSqlBtn.addActionListener(e -> {
            String sql = JOptionPane.showInputDialog(mainFrame, "Enter SQL (SELECT to show results):");
            if (sql == null || sql.trim().isEmpty()) return;
            try (Statement s = connection.createStatement()) {
                if (sql.trim().toLowerCase().startsWith("select")) {
                    ResultSet rs = s.executeQuery(sql);
                    DefaultTableModel m = buildTableModel(rs);
                    tableView.setModel(m);
                } else {
                    int rows = s.executeUpdate(sql);
                    messages.append("Query executed, " + rows + " rows affected.\n");
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(mainFrame, "SQL error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Disconnect
        disconnectBtn.addActionListener(e -> {
            try { if (connection != null && !connection.isClosed()) connection.close(); } catch (SQLException ignore) {}
            mainFrame.dispose();
            createAndShowLogin();
        });

        mainFrame.setVisible(true);
    }

    private static ActionListener thisListener() {
        return tableBox.getActionListeners()[0];
    }

    /* -------------------- Load Table Data -------------------- */
    private static void loadAndShowTable(String tableName, JTextArea messages) {
        if (tableName == null) return;
        try {
            TableMetadata meta = getTableMetadata(tableName);
            DefaultTableModel raw = null;
            try (Statement s = connection.createStatement();
                 ResultSet rs = s.executeQuery("SELECT * FROM " + quoteIdentifier(tableName))) {
                raw = buildTableModel(rs);
            }
            DatabaseTableModel model = new DatabaseTableModel(tableName, raw, meta);
            currentModel = model;

            JTableHeader header = tableView.getTableHeader();
            tableView.setModel(model);
            
            // Set custom editors for ENUM columns
            setupEnumEditors(tableView, meta);
            
            header.setDefaultRenderer(new HeaderRenderer(tableView, meta));
            tableView.setRowHeight(24);

            // Resize columns to fit
            TableColumnModel cm = tableView.getColumnModel();
            for (int i = 0; i < cm.getColumnCount(); i++) {
                TableColumn col = cm.getColumn(i);
                int pref = Math.min(300, Math.max(75, model.getColumnName(i).length()*10));
                col.setPreferredWidth(pref);
            }

            messages.append("Loaded table: " + tableName + "\n");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(mainFrame, "Error loading table: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /* -------------------- ENUM Editor Setup -------------------- */
    private static void setupEnumEditors(JTable table, TableMetadata meta) {
        for (int i = 0; i < meta.columns.size(); i++) {
            ColumnInfo col = meta.columns.get(i);
            if (col.enumValues != null && !col.enumValues.isEmpty()) {
                TableColumn column = table.getColumnModel().getColumn(i);
                JComboBox<String> comboBox = new JComboBox<>(col.enumValues.toArray(new String[0]));
                comboBox.setEditable(false); // Don't allow custom values for ENUM
                column.setCellEditor(new DefaultCellEditor(comboBox));
                
                // Also set a custom renderer to show the enum value
                column.setCellRenderer(new DefaultTableCellRenderer() {
                    @Override
                    public Component getTableCellRendererComponent(JTable table, Object value,
                            boolean isSelected, boolean hasFocus, int row, int column) {
                        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                        return this;
                    }
                });
            }
        }
    }

    /* -------------------- Metadata & Model -------------------- */
    private static TableMetadata getTableMetadata(String tableName) throws SQLException {
        if (tableMetaCache.containsKey(tableName)) return tableMetaCache.get(tableName);
        DatabaseMetaData md = connection.getMetaData();
        ResultSet cols = md.getColumns(null, null, tableName, null);
        List<ColumnInfo> columns = new ArrayList<>();
        while (cols.next()) {
            String name = cols.getString("COLUMN_NAME");
            int jdbcType = cols.getInt("DATA_TYPE");
            String typeName = cols.getString("TYPE_NAME");
            int nullable = cols.getInt("NULLABLE");
            
            // Check if this is an ENUM column and get its values
            List<String> enumValues = null;
            if ("ENUM".equalsIgnoreCase(typeName) || "SET".equalsIgnoreCase(typeName)) {
                enumValues = getEnumValues(tableName, name);
            }
            
            columns.add(new ColumnInfo(name, jdbcType, typeName, nullable == DatabaseMetaData.columnNullable, enumValues));
        }
        cols.close();

        // primary keys
        ResultSet pks = md.getPrimaryKeys(null, null, tableName);
        List<String> pkCols = new ArrayList<>();
        while (pks.next()) pkCols.add(pks.getString("COLUMN_NAME"));
        pks.close();

        // foreign keys (imported)
        ResultSet fks = md.getImportedKeys(null, null, tableName);
        List<ForeignKeyInfo> foreignKeys = new ArrayList<>();
        while (fks.next()) {
            foreignKeys.add(new ForeignKeyInfo(
                    fks.getString("FKCOLUMN_NAME"),
                    fks.getString("PKTABLE_NAME"),
                    fks.getString("PKCOLUMN_NAME")
            ));
        }
        fks.close();

        // exported keys (referencing this table)
        ResultSet eks = md.getExportedKeys(null, null, tableName);
        List<ForeignKeyInfo> exported = new ArrayList<>();
        while (eks.next()) {
            exported.add(new ForeignKeyInfo(
                    eks.getString("PKCOLUMN_NAME"),
                    eks.getString("FKTABLE_NAME"),
                    eks.getString("FKCOLUMN_NAME")
            ));
        }
        eks.close();

        TableMetadata meta = new TableMetadata(tableName, columns, pkCols, foreignKeys, exported);
        tableMetaCache.put(tableName, meta);
        return meta;
    }

    private static List<String> getEnumValues(String tableName, String columnName) throws SQLException {
        List<String> values = new ArrayList<>();
        // Query to get column definition
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM " + quoteIdentifier(tableName) + " LIKE '" + columnName + "'")) {
            if (rs.next()) {
                String type = rs.getString("Type");
                // Parse ENUM('value1','value2',...) or SET('value1','value2',...)
                if (type != null && (type.toUpperCase().startsWith("ENUM") || type.toUpperCase().startsWith("SET"))) {
                    String enumStr = type.substring(type.indexOf('(') + 1, type.lastIndexOf(')'));
                    String[] parts = enumStr.split(",");
                    for (String part : parts) {
                        String value = part.trim().replaceAll("^'|'$", "");
                        values.add(value);
                    }
                }
            }
        }
        return values;
    }

    /* -------------------- Custom TableModel -------------------- */
    private static class DatabaseTableModel extends DefaultTableModel {
        private final String tableName;
        private final TableMetadata meta;
        private final List<Integer> rowStatus = new ArrayList<>(); // 0=unchanged, 1=updated, 2=deleted, 3=inserted
        private final List<Vector<Object>> originalRows = new ArrayList<>();
        private boolean hasInsertRow = false;

        public DatabaseTableModel(String tableName, DefaultTableModel base, TableMetadata meta) {
            super();
            this.tableName = tableName;
            this.meta = meta;

            // Build model without marker column
            Vector<String> cols = new Vector<>();
            for (int i = 0; i < base.getColumnCount(); i++) cols.add(base.getColumnName(i));
            setColumnIdentifiers(cols);

            // Copy rows
            for (int r = 0; r < base.getRowCount(); r++) {
                Vector<Object> row = new Vector<>();
                Vector<?> baseRow = base.getDataVector().get(r);
                for (int c = 0; c < baseRow.size(); c++) row.add(baseRow.get(c));
                addRow(row);
                rowStatus.add(0); // unchanged
                originalRows.add(new Vector<>(row));
            }

            // Add empty row for insertion
            addNewRow();
        }

        public String getTableName() { return tableName; }

        public void addNewRow() {
            Vector<Object> newRow = new Vector<>();
            for (int i = 0; i < meta.columns.size(); i++) newRow.add(null);
            addRow(newRow);
            rowStatus.add(3); // new row
            originalRows.add(new Vector<>(newRow));
            hasInsertRow = true;
        }

        public void markRowsForDeletion(int[] rows) {
            // Sort in descending order to avoid index issues when deleting
            Arrays.sort(rows);
            for (int i = rows.length - 1; i >= 0; i--) {
                int row = rows[i];
                if (rowStatus.get(row) == 3) {
                    // New row - remove completely
                    removeRow(row);
                    rowStatus.remove(row);
                    originalRows.remove(row);
                } else {
                    // Existing row - mark for deletion
                    rowStatus.set(row, 2);
                }
            }
            fireTableDataChanged();
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return true;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex >= 0 && columnIndex < meta.columns.size()) {
                ColumnInfo ci = meta.columns.get(columnIndex);
                if (ci.enumValues != null && !ci.enumValues.isEmpty()) {
                    return String.class; // ENUM columns use String class with combo editor
                }
                int jdbc = ci.jdbcType;
                switch (jdbc) {
                    case Types.BIT:
                    case Types.BOOLEAN: return Boolean.class;
                    case Types.INTEGER:
                    case Types.SMALLINT:
                    case Types.TINYINT: return Integer.class;
                    case Types.BIGINT: return Long.class;
                    case Types.FLOAT:
                    case Types.REAL: return Float.class;
                    case Types.DOUBLE: return Double.class;
                    default: return Object.class;
                }
            }
            return Object.class;
        }

        @Override
        public void setValueAt(Object aValue, int row, int column) {
            super.setValueAt(aValue, row, column);
            int status = rowStatus.get(row);
            if (status == 0) {
                // Only mark as updated if the value actually changed
                Object originalValue = originalRows.get(row).get(column);
                if (!Objects.equals(originalValue, aValue)) {
                    rowStatus.set(row, 1); // updated
                }
            }
            // Status 3 (inserted) remains as is
            fireTableCellUpdated(row, column);
        }

        // FIXED: Only report real changes, not the insert row
        public boolean hasRealChanges() {
            for (int i = 0; i < rowStatus.size(); i++) {
                int status = rowStatus.get(i);
                // Don't count the insert row unless it has actual data
                if (status == 3) {
                    if (isRowEmpty(i)) {
                        continue; // Empty insert row doesn't count as a real change
                    }
                }
                if (status != 0) return true;
            }
            return false;
        }

        private boolean isRowEmpty(int row) {
            Vector<Object> rowData = (Vector<Object>) getDataVector().get(row);
            for (Object value : rowData) {
                if (value != null && !value.toString().isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        public boolean hasPendingChanges() {
            for (int s : rowStatus) if (s != 0) return true;
            return false;
        }

        public void discardChanges() {
            setRowCount(0);
            rowStatus.clear();
            originalRows.clear();
            hasInsertRow = false;
            
            // Reload from original data (excluding the insert row)
            for (Vector<Object> orig : originalRows) {
                if (rowStatus.size() > getRowCount() && rowStatus.get(getRowCount()) != 3) {
                    Vector<Object> copy = new Vector<>(orig);
                    addRow(copy);
                    rowStatus.add(0);
                }
            }
            
            // Add new insert row
            addNewRow();
            fireTableDataChanged();
        }

        public boolean applyChanges(JTextArea messages) {
            try {
                if (meta.primaryKeys.isEmpty()) {
                    boolean hasDUp = rowStatus.stream().anyMatch(s -> s == 1 || s == 2);
                    if (hasDUp) {
                        JOptionPane.showMessageDialog(mainFrame, "This table has no primary key. Updates/Deletes are not allowed.", "Schema restriction", JOptionPane.WARNING_MESSAGE);
                        return false;
                    }
                }

                List<RowChange> inserts = new ArrayList<>();
                List<RowChange> updates = new ArrayList<>();
                List<RowChange> deletes = new ArrayList<>();

                for (int r = 0; r < getRowCount(); r++) {
                    int s = rowStatus.get(r);
                    if (s == 2) { // deleted
                        RowChange rc = new RowChange(r, (Vector<Object>) getDataVector().get(r));
                        deletes.add(rc);
                    } else if (s == 1) { // updated
                        RowChange rc = new RowChange(r, (Vector<Object>) getDataVector().get(r));
                        updates.add(rc);
                    } else if (s == 3) { // inserted
                        Vector<Object> row = (Vector<Object>) getDataVector().get(r);
                        if (!isRowEmpty(r)) { // Only insert non-empty rows
                            RowChange rc = new RowChange(r, row);
                            inserts.add(rc);
                        }
                    }
                }

                // Validation
                for (RowChange rc : inserts) {
                    String msg = validateRowTypes(rc.values);
                    if (msg != null) {
                        JOptionPane.showMessageDialog(mainFrame, msg, "Validation error", JOptionPane.ERROR_MESSAGE);
                        return false;
                    }
                    String fkMsg = validateForeignKeys(rc.values);
                    if (fkMsg != null) {
                        JOptionPane.showMessageDialog(mainFrame, fkMsg, "Foreign key missing", JOptionPane.ERROR_MESSAGE);
                        return false;
                    }
                }
                for (RowChange rc : updates) {
                    String msg = validateRowTypes(rc.values);
                    if (msg != null) {
                        JOptionPane.showMessageDialog(mainFrame, msg, "Validation error", JOptionPane.ERROR_MESSAGE);
                        return false;
                    }
                    String fkMsg = validateForeignKeys(rc.values);
                    if (fkMsg != null) {
                        JOptionPane.showMessageDialog(mainFrame, fkMsg, "Foreign key missing", JOptionPane.ERROR_MESSAGE);
                        return false;
                    }
                }

                // Check deletes for cascading references
                for (RowChange rc : deletes) {
                    if (meta.primaryKeys.isEmpty()) continue;
                    Map<String, Object> pkMap = pkMapForRow(rc.values);
                    for (ForeignKeyInfo ek : meta.exportedKeys) {
                        String otherTable = ek.foreignTable;
                        String otherCol = ek.foreignColumn;
                        String pkCol = ek.localColumn;
                        Object pkVal = pkMap.get(pkCol);
                        if (pkVal == null) continue;
                        String checkSql = String.format("SELECT COUNT(*) FROM %s WHERE %s = ?", quoteIdentifier(otherTable), quoteIdentifier(otherCol));
                        try (PreparedStatement ps = connection.prepareStatement(checkSql)) {
                            ps.setObject(1, pkVal);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next() && rs.getInt(1) > 0) {
                                    int opt = JOptionPane.showConfirmDialog(mainFrame,
                                            String.format("Deleting this row will affect %s.%s (referencing rows exist). Continue?", otherTable, otherCol),
                                            "Cascade warning",
                                            JOptionPane.YES_NO_OPTION,
                                            JOptionPane.WARNING_MESSAGE);
                                    if (opt != JOptionPane.YES_OPTION) return false;
                                }
                            }
                        }
                    }
                }

                // Execute in transaction
                connection.setAutoCommit(false);
                try {
                    // Deletes
                    for (RowChange rc : deletes) {
                        if (meta.primaryKeys.isEmpty()) continue;
                        Map<String, Object> pkMap = pkMapForRow(rc.values);
                        StringBuilder where = new StringBuilder();
                        List<Object> params = new ArrayList<>();
                        for (int i = 0; i < meta.primaryKeys.size(); i++) {
                            if (i > 0) where.append(" AND ");
                            String pk = meta.primaryKeys.get(i);
                            where.append(quoteIdentifier(pk)).append(" = ?");
                            params.add(pkMap.get(pk));
                        }
                        String sql = String.format("DELETE FROM %s WHERE %s", quoteIdentifier(tableName), where.toString());
                        try (PreparedStatement ps = connection.prepareStatement(sql)) {
                            for (int i = 0; i < params.size(); i++) ps.setObject(i+1, params.get(i));
                            int affected = ps.executeUpdate();
                            messages.append("Deleted rows: " + affected + "\n");
                        }
                    }

                    // Inserts
                    for (RowChange rc : inserts) {
                        List<String> colNames = new ArrayList<>();
                        List<Object> vals = new ArrayList<>();
                        for (int c = 0; c < rc.values.size(); c++) {
                            Object v = rc.values.get(c);
                            if (v != null) { // Only include non-null values
                                colNames.add(quoteIdentifier(meta.columns.get(c).name));
                                vals.add(v);
                            }
                        }
                        if (colNames.isEmpty()) continue;
                        String qCols = String.join(", ", colNames);
                        String qPlaceholders = String.join(", ", Collections.nCopies(vals.size(), "?"));
                        String sql = String.format("INSERT INTO %s (%s) VALUES (%s)", quoteIdentifier(tableName), qCols, qPlaceholders);
                        try (PreparedStatement ps = connection.prepareStatement(sql)) {
                            for (int i = 0; i < vals.size(); i++) ps.setObject(i+1, vals.get(i));
                            int affected = ps.executeUpdate();
                            messages.append("Inserted rows: " + affected + "\n");
                        }
                    }

                    // Updates
                    for (RowChange rc : updates) {
                        if (meta.primaryKeys.isEmpty()) continue;
                        Map<String, Object> pkMap = pkMapForRow(rc.values);
                        StringBuilder set = new StringBuilder();
                        List<Object> params = new ArrayList<>();
                        for (int c = 0; c < rc.values.size(); c++) {
                            String col = meta.columns.get(c).name;
                            if (meta.primaryKeys.contains(col)) continue;
                            if (set.length() > 0) set.append(", ");
                            set.append(quoteIdentifier(col)).append(" = ?");
                            params.add(rc.values.get(c));
                        }
                        if (set.length() == 0) continue;
                        StringBuilder where = new StringBuilder();
                        for (int i = 0; i < meta.primaryKeys.size(); i++) {
                            if (i > 0) where.append(" AND ");
                            String pk = meta.primaryKeys.get(i);
                            where.append(quoteIdentifier(pk)).append(" = ?");
                            params.add(pkMap.get(pk));
                        }
                        String sql = String.format("UPDATE %s SET %s WHERE %s", quoteIdentifier(tableName), set.toString(), where.toString());
                        try (PreparedStatement ps = connection.prepareStatement(sql)) {
                            for (int i = 0; i < params.size(); i++) ps.setObject(i+1, params.get(i));
                            int affected = ps.executeUpdate();
                            messages.append("Updated rows: " + affected + "\n");
                        }
                    }

                    connection.commit();
                    messages.append("All changes applied successfully.\n");

                    // Refresh model
                    try (Statement s = connection.createStatement();
                         ResultSet rs = s.executeQuery("SELECT * FROM " + quoteIdentifier(tableName))) {
                        DefaultTableModel fresh = buildTableModel(rs);
                        setRowCount(0);
                        Vector<String> cols = new Vector<>();
                        for (int i = 0; i < fresh.getColumnCount(); i++) cols.add(fresh.getColumnName(i));
                        setColumnIdentifiers(cols);
                        for (int r = 0; r < fresh.getRowCount(); r++) {
                            Vector<Object> newRow = new Vector<>();
                            Vector<?> baseRow = fresh.getDataVector().get(r);
                            for (Object o : baseRow) newRow.add(o);
                            addRow(newRow);
                        }
                        
                        // Reset tracking
                        rowStatus.clear();
                        originalRows.clear();
                        for (int i = 0; i < getRowCount(); i++) {
                            rowStatus.add(0);
                            originalRows.add(new Vector<>((Vector<Object>) getDataVector().get(i)));
                        }
                        
                        // Add new insert row
                        addNewRow();
                    }

                    return true;
                } catch (SQLException e) {
                    connection.rollback();
                    JOptionPane.showMessageDialog(mainFrame, "Error applying changes: " + e.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
                    return false;
                } finally {
                    try { connection.setAutoCommit(true); } catch (SQLException ignore) {}
                }

            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(mainFrame, "Validation error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }

        private Map<String, Object> pkMapForRow(Vector<Object> values) {
            Map<String, Object> map = new HashMap<>();
            for (String pk : meta.primaryKeys) {
                int idx = -1;
                for (int i = 0; i < meta.columns.size(); i++) if (meta.columns.get(i).name.equals(pk)) { idx = i; break; }
                if (idx >= 0) {
                    Object val = values.get(idx);
                    map.put(pk, val);
                }
            }
            return map;
        }

        private String validateRowTypes(Vector<Object> values) {
            for (int c = 0; c < values.size(); c++) {
                ColumnInfo col = meta.columns.get(c);
                Object val = values.get(c);
                if (val == null || (val instanceof String && ((String)val).isEmpty())) {
                    if (!col.nullable) {
                        return String.format("'%s' should be of type %s and NOT NULL.", col.name, col.typeName);
                    } else continue;
                }
                try {
                    switch (col.jdbcType) {
                        case Types.INTEGER:
                        case Types.SMALLINT:
                        case Types.TINYINT:
                            Integer.parseInt(val.toString());
                            break;
                        case Types.BIGINT:
                            Long.parseLong(val.toString());
                            break;
                        case Types.FLOAT:
                        case Types.REAL:
                        case Types.DOUBLE:
                        case Types.DECIMAL:
                        case Types.NUMERIC:
                            Double.parseDouble(val.toString());
                            break;
                        default:
                            break;
                    }
                } catch (NumberFormatException nfe) {
                    return String.format("'%s' should be of type %s (value '%s' is invalid).", col.name, col.typeName, val);
                }
            }
            return null;
        }

        private String validateForeignKeys(Vector<Object> values) throws SQLException {
            for (ForeignKeyInfo fk : meta.foreignKeys) {
                int localIdx = -1;
                for (int i = 0; i < meta.columns.size(); i++) if (meta.columns.get(i).name.equals(fk.localColumn)) { localIdx = i; break; }
                if (localIdx < 0) continue;
                Object val = values.get(localIdx);
                if (val == null) continue;
                String checkSql = String.format("SELECT COUNT(*) FROM %s WHERE %s = ?", quoteIdentifier(fk.foreignTable), quoteIdentifier(fk.foreignColumn));
                try (PreparedStatement ps = connection.prepareStatement(checkSql)) {
                    ps.setObject(1, val);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) == 0) {
                            return String.format("'%s' references %s(%s), but value '%s' doesn't exist.",
                                    fk.localColumn, fk.foreignTable, fk.foreignColumn, val);
                        }
                    }
                }
            }
            return null;
        }

        public String getColumnTooltip(int colIndex) {
            if (colIndex < 0 || colIndex >= meta.columns.size()) return null;
            ColumnInfo ci = meta.columns.get(colIndex);
            StringBuilder sb = new StringBuilder();
            sb.append(ci.name).append(" : ").append(ci.typeName);
            if (!ci.nullable) sb.append(" (NOT NULL)");
            if (ci.enumValues != null && !ci.enumValues.isEmpty()) {
                sb.append(" ENUM: ").append(String.join(", ", ci.enumValues));
            }
            for (ForeignKeyInfo fk : meta.foreignKeys) {
                if (fk.localColumn.equals(ci.name)) {
                    sb.append("  FK -> ").append(fk.foreignTable).append("(").append(fk.foreignColumn).append(")");
                }
            }
            return sb.toString();
        }
    }

    /* -------------------- Helpers & Model Builders -------------------- */
    private static DefaultTableModel buildTableModel(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        Vector<String> colNames = new Vector<>();
        int colCount = meta.getColumnCount();
        for (int i = 1; i <= colCount; i++) colNames.add(meta.getColumnName(i));
        Vector<Vector<Object>> data = new Vector<>();
        while (rs.next()) {
            Vector<Object> row = new Vector<>();
            for (int i = 1; i <= colCount; i++) {
                row.add(rs.getObject(i));
            }
            data.add(row);
        }
        return new DefaultTableModel(data, colNames);
    }

    private static String quoteIdentifier(String id) {
        return "`" + id.replace("`", "``") + "`";
    }

    /* -------------------- Metadata classes -------------------- */
    private static class TableMetadata {
        final String tableName;
        final List<ColumnInfo> columns;
        final List<String> primaryKeys;
        final List<ForeignKeyInfo> foreignKeys;
        final List<ForeignKeyInfo> exportedKeys;

        TableMetadata(String tableName, List<ColumnInfo> columns, List<String> primaryKeys, List<ForeignKeyInfo> foreignKeys, List<ForeignKeyInfo> exportedKeys) {
            this.tableName = tableName;
            this.columns = columns;
            this.primaryKeys = primaryKeys;
            this.foreignKeys = foreignKeys;
            this.exportedKeys = exportedKeys;
        }
    }

    private static class ColumnInfo {
        final String name;
        final int jdbcType;
        final String typeName;
        final boolean nullable;
        final List<String> enumValues; // NEW: Store ENUM values
        
        ColumnInfo(String name, int jdbcType, String typeName, boolean nullable, List<String> enumValues) {
            this.name = name; 
            this.jdbcType = jdbcType; 
            this.typeName = typeName; 
            this.nullable = nullable;
            this.enumValues = enumValues;
        }
    }

    private static class ForeignKeyInfo {
        final String localColumn;
        final String foreignTable;
        final String foreignColumn;
        ForeignKeyInfo(String localColumn, String foreignTable, String foreignColumn) {
            this.localColumn = localColumn; this.foreignTable = foreignTable; this.foreignColumn = foreignColumn;
        }
    }

    private static class RowChange {
        final int rowIndex;
        final Vector<Object> values;
        RowChange(int rowIndex, Vector<Object> values) { this.rowIndex = rowIndex; this.values = values; }
    }

    /* -------------------- Header Renderer -------------------- */
    private static class HeaderRenderer implements TableCellRenderer {
        private final JTable table;
        private final TableMetadata meta;
        HeaderRenderer(JTable table, TableMetadata meta) {
            this.table = table; this.meta = meta;
        }
        @Override
        public Component getTableCellRendererComponent(JTable tbl, Object val, boolean isSelected, boolean hasFocus, int row, int col) {
            JLabel lbl = new JLabel();
            lbl.setOpaque(true);
            lbl.setBorder(UIManager.getBorder("TableHeader.cellBorder"));
            lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
            lbl.setForeground(new Color(230,230,230));
            lbl.setBackground(new Color(60,60,60));
            String text = val == null ? "" : val.toString();
            if (col >=0 && col < meta.columns.size()) {
                ColumnInfo ci = meta.columns.get(col);
                lbl.setText(ci.name + "  ");
            } else lbl.setText(text);
            return lbl;
        }
    }

    /* -------------------- Utilities -------------------- */
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
        UIManager.put("Table.background", new Color(35, 35, 35));
        UIManager.put("Table.foreground", fg);
        UIManager.put("Table.selectionBackground", new Color(80, 120, 160));
        UIManager.put("Table.selectionForeground", Color.WHITE);
        UIManager.put("Table.gridColor", new Color(60,60,60));
    }
}
