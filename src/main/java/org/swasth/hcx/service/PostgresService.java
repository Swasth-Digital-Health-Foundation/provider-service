package org.swasth.hcx.service;


import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.swasth.hcx.exception.ClientException;

import java.sql.*;

@Service
public class PostgresService {

    @Value("${postgres.url}")
    private String url;

    @Value("${postgres.user}")
    private String user;

    @Value("${postgres.password}")
    private String password;
    private Connection connection;
    private Statement statement;

    @PostConstruct
    public void init() throws SQLException, ClientException {
        this.connection = getConnection();
        this.statement = this.connection.createStatement();
    }

    public Connection getConnection() throws ClientException {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(url, user, password);
            }
            return connection;
        } catch (SQLException e) {
            throw new ClientException("Error connecting to the PostgreSQL server: " + e.getMessage());
        }
    }

    public void close() throws SQLException {
        statement.close();
        connection.close();
    }

    public boolean execute(String query) throws ClientException {
        try {
            return statement.execute(query);
        } catch (Exception e) {
            throw new ClientException("Error while performing database operation: " + e.getMessage());
        }
    }

    public ResultSet executeQuery(String query) throws ClientException {
        try {
            return statement.executeQuery(query);
        } catch (Exception e) {
            throw new ClientException("Error while performing database operation: " + e.getMessage());
        }
    }

    public void addBatch(String query) throws ClientException {
        try {
            statement.addBatch(query);
        } catch (Exception e) {
            throw new ClientException("Error while performing database operation: " + e.getMessage());
        }
    }

    public int[] executeBatch() throws ClientException {
        try {
            return statement.executeBatch();
        } catch (Exception e) {
            throw new ClientException("Error while performing database operation: " + e.getMessage());
        }
    }

    public boolean isHealthy() {
        try {
            Connection conn = getConnection();
            conn.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
