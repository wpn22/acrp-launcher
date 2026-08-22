package com.adventurecity.jobs.storage;

import com.adventurecity.jobs.config.PluginSettings;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite (default, zero setup) or MySQL, behind one dialect-aware implementation.
 *
 * <p>Every method here blocks. They are only ever called from the single IO thread owned by
 * {@link PlayerDataManager} - never from the server thread.</p>
 */
public final class SqlStorage {

    private final boolean mysql;
    private final String url;
    private final String username;
    private final String password;
    private final long startingBalance;
    private final Logger logger;

    private Connection connection;

    public SqlStorage(PluginSettings settings, File dataFolder, Logger logger) {
        this.mysql = settings.isMysql();
        this.startingBalance = settings.startingBalance;
        this.logger = logger;
        if (mysql) {
            this.url = "jdbc:mysql://" + settings.mysqlHost + ":" + settings.mysqlPort + "/"
                    + settings.mysqlDatabase
                    + "?useSSL=" + settings.mysqlUseSsl
                    + "&useUnicode=true&characterEncoding=utf8&autoReconnect=true";
            this.username = settings.mysqlUsername;
            this.password = settings.mysqlPassword;
        } else {
            this.url = "jdbc:sqlite:" + new File(dataFolder, "data.db").getAbsolutePath();
            this.username = null;
            this.password = null;
        }
    }

    public void init() throws SQLException {
        loadDriver();
        connect();
        createTables();
        logger.info("[ACRPJobs] Storage ready (" + (mysql ? "MySQL" : "SQLite") + ").");
    }

    private void loadDriver() {
        if (mysql) {
            if (!tryLoad("com.mysql.cj.jdbc.Driver") && !tryLoad("com.mysql.jdbc.Driver")) {
                logger.warning("[ACRPJobs] No MySQL driver found on the server - switch storage.type to sqlite.");
            }
        } else {
            tryLoad("org.sqlite.JDBC");
        }
    }

    private boolean tryLoad(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    private void connect() throws SQLException {
        connection = mysql
                ? DriverManager.getConnection(url, username, password)
                : DriverManager.getConnection(url);
    }

    private Connection connection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(3)) {
            connect();
        }
        return connection;
    }

    private void createTables() throws SQLException {
        String autoId = mysql
                ? "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY"
                : "id INTEGER PRIMARY KEY AUTOINCREMENT";

        execute("CREATE TABLE IF NOT EXISTS acrp_players ("
                + "uuid VARCHAR(36) NOT NULL,"
                + "name VARCHAR(32) NOT NULL,"
                + "balance BIGINT NOT NULL DEFAULT 0,"
                + "current_job VARCHAR(48),"
                + "on_duty INT NOT NULL DEFAULT 0,"
                + "last_seen BIGINT NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (uuid))");

        execute("CREATE TABLE IF NOT EXISTS acrp_player_jobs ("
                + "uuid VARCHAR(36) NOT NULL,"
                + "job_id VARCHAR(48) NOT NULL,"
                + "grade INT NOT NULL DEFAULT 1,"
                + "xp BIGINT NOT NULL DEFAULT 0,"
                + "hired_at BIGINT NOT NULL DEFAULT 0,"
                + "lesson INT NOT NULL DEFAULT 0,"
                + "met_trainer INT NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (uuid, job_id))");

        // Servers that ran an earlier build already have the table; these fail harmlessly there.
        executeQuietly("ALTER TABLE acrp_player_jobs ADD COLUMN lesson INT NOT NULL DEFAULT 0");
        executeQuietly("ALTER TABLE acrp_player_jobs ADD COLUMN met_trainer INT NOT NULL DEFAULT 0");

        execute("CREATE TABLE IF NOT EXISTS acrp_transactions ("
                + autoId + ","
                + "uuid VARCHAR(36) NOT NULL,"
                + "type VARCHAR(32) NOT NULL,"
                + "amount BIGINT NOT NULL,"
                + "balance_after BIGINT NOT NULL,"
                + "reason VARCHAR(160),"
                + "created_at BIGINT NOT NULL)");

        execute("CREATE TABLE IF NOT EXISTS acrp_contract_log ("
                + autoId + ","
                + "uuid VARCHAR(36) NOT NULL,"
                + "job_id VARCHAR(48) NOT NULL,"
                + "contract_id VARCHAR(48) NOT NULL,"
                + "pay BIGINT NOT NULL,"
                + "duration_sec INT NOT NULL,"
                + "created_at BIGINT NOT NULL)");

        // MySQL has no CREATE INDEX IF NOT EXISTS, so a duplicate here is expected on every boot.
        executeQuietly("CREATE INDEX idx_acrp_tx_player ON acrp_transactions (uuid, created_at)");
        executeQuietly("CREATE INDEX idx_acrp_contract_player ON acrp_contract_log (uuid, created_at)");
        executeQuietly("CREATE INDEX idx_acrp_players_balance ON acrp_players (balance)");
    }

    private void execute(String sql) throws SQLException {
        Statement statement = connection().createStatement();
        try {
            statement.execute(sql);
        } finally {
            statement.close();
        }
    }

    private void executeQuietly(String sql) {
        try {
            execute(sql);
        } catch (SQLException ex) {
            logger.log(Level.FINE, "[ACRPJobs] Skipped: " + sql, ex);
        }
    }

    // ---------------------------------------------------------------- players

    /** Loads the player, creating the row (and the starting balance) on first join. */
    public PlayerData load(UUID uuid, String name) throws SQLException {
        PlayerData data = null;
        PreparedStatement select = connection().prepareStatement(
                "SELECT name, balance, current_job, on_duty FROM acrp_players WHERE uuid=?");
        try {
            select.setString(1, uuid.toString());
            ResultSet rs = select.executeQuery();
            try {
                if (rs.next()) {
                    data = new PlayerData(uuid, name, rs.getLong("balance"),
                            rs.getString("current_job"), rs.getInt("on_duty") == 1);
                }
            } finally {
                rs.close();
            }
        } finally {
            select.close();
        }

        if (data == null) {
            data = new PlayerData(uuid, name, startingBalance, null, false);
            insertPlayer(uuid, name, startingBalance);
            logTransaction(uuid, TxType.FIRST_JOIN, startingBalance, startingBalance, "رصيد البداية");
            return data;
        }

        PreparedStatement jobs = connection().prepareStatement(
                "SELECT job_id, grade, xp, hired_at, lesson, met_trainer FROM acrp_player_jobs WHERE uuid=?");
        try {
            jobs.setString(1, uuid.toString());
            ResultSet rs = jobs.executeQuery();
            try {
                while (rs.next()) {
                    data.addJob(new JobProgress(rs.getString("job_id"), rs.getInt("grade"),
                            rs.getLong("xp"), rs.getLong("hired_at"),
                            rs.getInt("lesson"), rs.getInt("met_trainer") == 1));
                }
            } finally {
                rs.close();
            }
        } finally {
            jobs.close();
        }
        data.dirty(false);
        return data;
    }

    private void insertPlayer(UUID uuid, String name, long balance) throws SQLException {
        PreparedStatement insert = connection().prepareStatement(
                "INSERT INTO acrp_players (uuid, name, balance, current_job, on_duty, last_seen) "
                        + "VALUES (?, ?, ?, NULL, 0, ?)");
        try {
            insert.setString(1, uuid.toString());
            insert.setString(2, name);
            insert.setLong(3, balance);
            insert.setLong(4, System.currentTimeMillis());
            insert.executeUpdate();
        } finally {
            insert.close();
        }
    }

    /** Update-then-insert keeps this dialect-neutral (no ON DUPLICATE KEY / INSERT OR REPLACE). */
    public void save(PlayerData data) throws SQLException {
        PreparedStatement update = connection().prepareStatement(
                "UPDATE acrp_players SET name=?, balance=?, current_job=?, on_duty=?, last_seen=? WHERE uuid=?");
        int rows;
        try {
            update.setString(1, data.name());
            update.setLong(2, data.balance());
            update.setString(3, data.currentJob());
            update.setInt(4, data.onDuty() ? 1 : 0);
            update.setLong(5, System.currentTimeMillis());
            update.setString(6, data.uuid().toString());
            rows = update.executeUpdate();
        } finally {
            update.close();
        }
        if (rows == 0) {
            insertPlayer(data.uuid(), data.name(), data.balance());
        }

        for (JobProgress progress : data.jobs()) {
            PreparedStatement jobUpdate = connection().prepareStatement(
                    "UPDATE acrp_player_jobs SET grade=?, xp=?, lesson=?, met_trainer=? WHERE uuid=? AND job_id=?");
            int jobRows;
            try {
                jobUpdate.setInt(1, progress.gradeLevel());
                jobUpdate.setLong(2, progress.xp());
                jobUpdate.setInt(3, progress.lesson());
                jobUpdate.setInt(4, progress.metTrainer() ? 1 : 0);
                jobUpdate.setString(5, data.uuid().toString());
                jobUpdate.setString(6, progress.jobId());
                jobRows = jobUpdate.executeUpdate();
            } finally {
                jobUpdate.close();
            }
            if (jobRows == 0) {
                PreparedStatement jobInsert = connection().prepareStatement(
                        "INSERT INTO acrp_player_jobs (uuid, job_id, grade, xp, hired_at, lesson, met_trainer)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?)");
                try {
                    jobInsert.setString(1, data.uuid().toString());
                    jobInsert.setString(2, progress.jobId());
                    jobInsert.setInt(3, progress.gradeLevel());
                    jobInsert.setLong(4, progress.xp());
                    jobInsert.setLong(5, progress.hiredAt());
                    jobInsert.setInt(6, progress.lesson());
                    jobInsert.setInt(7, progress.metTrainer() ? 1 : 0);
                    jobInsert.executeUpdate();
                } finally {
                    jobInsert.close();
                }
            }
        }
    }

    public void deleteJob(UUID uuid, String jobId) throws SQLException {
        PreparedStatement delete = connection().prepareStatement(
                "DELETE FROM acrp_player_jobs WHERE uuid=? AND job_id=?");
        try {
            delete.setString(1, uuid.toString());
            delete.setString(2, jobId);
            delete.executeUpdate();
        } finally {
            delete.close();
        }
    }

    // ---------------------------------------------------------------- audit log

    public void logTransaction(UUID uuid, String type, long amount, long balanceAfter, String reason) {
        try {
            PreparedStatement insert = connection().prepareStatement(
                    "INSERT INTO acrp_transactions (uuid, type, amount, balance_after, reason, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)");
            try {
                insert.setString(1, uuid.toString());
                insert.setString(2, type);
                insert.setLong(3, amount);
                insert.setLong(4, balanceAfter);
                insert.setString(5, trim(reason, 160));
                insert.setLong(6, System.currentTimeMillis());
                insert.executeUpdate();
            } finally {
                insert.close();
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[ACRPJobs] Could not write transaction log", ex);
        }
    }

    public void logContract(UUID uuid, String jobId, String contractId, long pay, int durationSeconds) {
        try {
            PreparedStatement insert = connection().prepareStatement(
                    "INSERT INTO acrp_contract_log (uuid, job_id, contract_id, pay, duration_sec, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)");
            try {
                insert.setString(1, uuid.toString());
                insert.setString(2, jobId);
                insert.setString(3, contractId);
                insert.setLong(4, pay);
                insert.setInt(5, durationSeconds);
                insert.setLong(6, System.currentTimeMillis());
                insert.executeUpdate();
            } finally {
                insert.close();
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[ACRPJobs] Could not write contract log", ex);
        }
    }

    /** Sum of job earnings since a timestamp - the source of truth for the daily cap. */
    public long earnedSince(UUID uuid, long sinceMillis) throws SQLException {
        PreparedStatement select = connection().prepareStatement(
                "SELECT COALESCE(SUM(amount), 0) FROM acrp_transactions "
                        + "WHERE uuid=? AND created_at>=? AND amount>0 AND type IN (?, ?, ?)");
        try {
            select.setString(1, uuid.toString());
            select.setLong(2, sinceMillis);
            select.setString(3, TxType.CONTRACT);
            select.setString(4, TxType.SALARY);
            select.setString(5, TxType.FARE);
            ResultSet rs = select.executeQuery();
            try {
                return rs.next() ? rs.getLong(1) : 0L;
            } finally {
                rs.close();
            }
        } finally {
            select.close();
        }
    }

    // ---------------------------------------------------------------- offline / lookups

    public UUID findUuidByName(String name) {
        try {
            PreparedStatement select = connection().prepareStatement(
                    "SELECT uuid FROM acrp_players WHERE LOWER(name)=LOWER(?)");
            try {
                select.setString(1, name);
                ResultSet rs = select.executeQuery();
                try {
                    if (rs.next()) {
                        return UUID.fromString(rs.getString(1));
                    }
                } finally {
                    rs.close();
                }
            } finally {
                select.close();
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[ACRPJobs] Name lookup failed", ex);
        } catch (IllegalArgumentException ex) {
            logger.warning("[ACRPJobs] Corrupt uuid stored for name " + name);
        }
        return null;
    }

    /**
     * Adjusts an offline player's balance directly in SQL. This is what store deliveries use, so it
     * has to work whether or not the player has ever joined.
     *
     * @return the new balance, or -1 when the operation failed
     */
    public long offlineAdjust(UUID uuid, String name, long amount, boolean absolute, String type, String reason) {
        try {
            long current = -1L;
            PreparedStatement select = connection().prepareStatement(
                    "SELECT balance FROM acrp_players WHERE uuid=?");
            try {
                select.setString(1, uuid.toString());
                ResultSet rs = select.executeQuery();
                try {
                    if (rs.next()) {
                        current = rs.getLong(1);
                    }
                } finally {
                    rs.close();
                }
            } finally {
                select.close();
            }

            if (current < 0L) {
                insertPlayer(uuid, name == null ? uuid.toString().substring(0, 8) : name, startingBalance);
                current = startingBalance;
            }

            long next = absolute ? Math.max(0L, amount) : Math.max(0L, current + amount);
            PreparedStatement update = connection().prepareStatement(
                    "UPDATE acrp_players SET balance=? WHERE uuid=?");
            try {
                update.setLong(1, next);
                update.setString(2, uuid.toString());
                update.executeUpdate();
            } finally {
                update.close();
            }
            logTransaction(uuid, type, next - current, next, reason);
            return next;
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[ACRPJobs] Offline balance update failed", ex);
            return -1L;
        }
    }

    /** name + balance pairs, richest first. */
    public List<Object[]> topBalances(int limit) throws SQLException {
        List<Object[]> out = new ArrayList<Object[]>();
        PreparedStatement select = connection().prepareStatement(
                "SELECT name, balance FROM acrp_players ORDER BY balance DESC LIMIT ?");
        try {
            select.setInt(1, limit);
            ResultSet rs = select.executeQuery();
            try {
                while (rs.next()) {
                    out.add(new Object[] { rs.getString(1), Long.valueOf(rs.getLong(2)) });
                }
            } finally {
                rs.close();
            }
        } finally {
            select.close();
        }
        return out;
    }

    /** { contracts completed, total earned from contracts } since a timestamp. */
    public long[] contractStats(UUID uuid, long sinceMillis) throws SQLException {
        PreparedStatement select = connection().prepareStatement(
                "SELECT COUNT(*), COALESCE(SUM(pay), 0) FROM acrp_contract_log WHERE uuid=? AND created_at>=?");
        try {
            select.setString(1, uuid.toString());
            select.setLong(2, sinceMillis);
            ResultSet rs = select.executeQuery();
            try {
                if (rs.next()) {
                    return new long[] { rs.getLong(1), rs.getLong(2) };
                }
            } finally {
                rs.close();
            }
        } finally {
            select.close();
        }
        return new long[] { 0L, 0L };
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ex) {
                logger.log(Level.FINE, "[ACRPJobs] Error closing connection", ex);
            }
        }
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
