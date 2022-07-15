package com.cloudbuild.gcp.cloudbuildgcp;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@WebListener("Creates a connection pool that is stored in the Servlet's context for later use.")
public class ConnectionPoolContextListener implements ServletContextListener {

    // Saving credentials in environment variables is convenient, but not secure - consider a more
    // secure solution such as https://cloud.google.com/kms/ to help keep secrets safe.
    @Value("${INSTANCE_CONNECTION_NAME}")
    private static String INSTANCE_CONNECTION_NAME;
    @Value("${JDBC_URL}")
    private static String JDBC_URL;
    @Value("${DB_USER}")
    private static String DB_USER;
    @Value("${DB_PASS}")
    private static String DB_PASS;
    @Value("${DB_NAME}")
    private static String DB_NAME;

    private static Logger logger = LoggerFactory.getLogger(ConnectionPoolContextListener.class);


    private DataSource createConnectionPool() {
        // [START cloud_sql_mysql_servlet_create]
        // Note: For Java users, the Cloud SQL JDBC Socket Factory can provide authenticated connections
        // which is preferred to using the Cloud SQL Proxy with Unix sockets.
        // See https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory for details.

        // The configuration object specifies behaviors for the connection pool.
        HikariConfig config = new HikariConfig();

        // The following URL is equivalent to setting the config options below:
        // jdbc:mysql:///<DB_NAME>?cloudSqlInstance=<INSTANCE_CONNECTION_NAME>&
        // socketFactory=com.google.cloud.sql.mysql.SocketFactory&user=<DB_USER>&password=<DB_PASS>
        // See the link below for more info on building a JDBC URL for the Cloud SQL JDBC Socket Factory
        // https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory#creating-the-jdbc-url

        // Configure which instance and what database user to connect with.
        config.setJdbcUrl(String.format("jdbc:mysql://%s:%s/%s", DB_NAME));
        config.setUsername(DB_USER); // e.g. "root", "mysql"
        config.setPassword(DB_PASS); // e.g. "my-password"

        config.addDataSourceProperty("socketFactory", "com.google.cloud.sql.mysql.SocketFactory");
        config.addDataSourceProperty("cloudSqlInstance", INSTANCE_CONNECTION_NAME);

        // The ipTypes argument can be used to specify a comma delimited list of preferred IP types
        // for connecting to a Cloud SQL instance. The argument ipTypes=PRIVATE will force the
        // SocketFactory to connect with an instance's associated private IP.
        config.addDataSourceProperty("ipTypes", "PRIVATE");

        // ... Specify additional connection properties here.
        // [START_EXCLUDE]

        // [START cloud_sql_mysql_servlet_limit]
        // maximumPoolSize limits the total number of concurrent connections this pool will keep. Ideal
        // values for this setting are highly variable on app design, infrastructure, and database.
        config.setMaximumPoolSize(5);
        // minimumIdle is the minimum number of idle connections Hikari maintains in the pool.
        // Additional connections will be established to meet this value unless the pool is full.
        config.setMinimumIdle(5);
        // [END cloud_sql_mysql_servlet_limit]

        // [START cloud_sql_mysql_servlet_timeout]
        // setConnectionTimeout is the maximum number of milliseconds to wait for a connection checkout.
        // Any attempt to retrieve a connection from this pool that exceeds the set limit will throw an
        // SQLException.
        config.setConnectionTimeout(60000); // 1 minute
        // idleTimeout is the maximum amount of time a connection can sit in the pool. Connections that
        // sit idle for this many milliseconds are retried if minimumIdle is exceeded.
        config.setIdleTimeout(600000); // 10 minutes
        // [END cloud_sql_mysql_servlet_timeout]

        // [START cloud_sql_mysql_servlet_backoff]
        // Hikari automatically delays between failed connection attempts, eventually reaching a
        // maximum delay of `connectionTimeout / 2` between attempts.
        // [END cloud_sql_mysql_servlet_backoff]

        // [START cloud_sql_mysql_servlet_lifetime]
        // maxLifetime is the maximum possible lifetime of a connection in the pool. Connections that
        // live longer than this many milliseconds will be closed and reestablished between uses. This
        // value should be several minutes shorter than the database's timeout value to avoid unexpected
        // terminations.
        config.setMaxLifetime(1800000); // 30 minutes
        // [END cloud_sql_mysql_servlet_lifetime]

        // [END_EXCLUDE]

        // Initialize the connection pool using the configuration object.
        DataSource pool = new HikariDataSource(config);
        // [END cloud_sql_mysql_servlet_create]
        return pool;
    }

    private void createTable(DataSource pool) throws SQLException {
        // Safely attempt to create the table schema.
        try (Connection conn = pool.getConnection()) {
            String stmt =
                    "CREATE TABLE IF NOT EXISTS votes ( "
                            + "vote_id SERIAL NOT NULL, time_cast timestamp NOT NULL, candidate CHAR(6) NOT NULL,"
                            + " PRIMARY KEY (vote_id) );";
            try (PreparedStatement createTableStatement = conn.prepareStatement(stmt);) {
                createTableStatement.execute();
            }
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        // This function is called when the Servlet is destroyed.
        HikariDataSource pool = (HikariDataSource) event.getServletContext().getAttribute("my-pool");
        if (pool != null) {
            pool.close();
        }
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        // This function is called when the application starts and will safely create a connection pool
        // that can be used to connect to.
        ServletContext servletContext = event.getServletContext();
        DataSource pool = (DataSource) servletContext.getAttribute("my-pool");
        logger.info("Creating Connection pool");
        if (pool == null) {
            pool = createConnectionPool();
            servletContext.setAttribute("my-pool", pool);
        }
        try {
            createTable(pool);
        } catch (SQLException ex) {
            throw new RuntimeException(
                    "Unable to verify table schema. Please double check the steps"
                            + "in the README and try again.",
                    ex);
        }
    }
}