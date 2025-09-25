package com.example;

import java.sql.*;
import java.io.Console;
import java.util.Scanner;

public class App {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        Console console = System.console();

        System.out.print("MySQL host (default: localhost): ");
        String host = scanner.nextLine().trim();
        if (host.isEmpty()) host = "localhost";

        System.out.print("Port (default: 3306): ");
        String portStr = scanner.nextLine().trim();
        if (portStr.isEmpty()) portStr = "3306";

        System.out.print("Database (default: test): ");
        String database = scanner.nextLine().trim();
        if (database.isEmpty()) database = "test";

        System.out.print("Username: ");
        String user = scanner.nextLine().trim();

        String password;
        if (console != null) {
            char[] pwd = console.readPassword("Password: ");
            password = (pwd == null) ? "" : new String(pwd);
        } else {
            // Console might be null in some environments (IDE), fallback:
            System.out.print("Password (visible): ");
            password = scanner.nextLine();
        }

        // JDBC URL: you can add options as needed (ssl, timezone, etc.)
        String url = String.format("jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                                   host, portStr, database);

        System.out.println("Connecting to: " + url);

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("Connected successfully!");

            try (PreparedStatement ps = conn.prepareStatement("SELECT 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("Test query returned: " + rs.getInt(1));
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("Failed to connect: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
