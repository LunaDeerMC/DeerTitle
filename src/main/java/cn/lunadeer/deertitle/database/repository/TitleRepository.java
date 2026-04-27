package cn.lunadeer.deertitle.database.repository;

import cn.lunadeer.deertitle.database.DatabaseManager;
import cn.lunadeer.deertitle.database.model.TitleRecord;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TitleRepository {

    private final DatabaseManager databaseManager;

    public TitleRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Optional<TitleRecord> findById(int id) throws Exception {
        try (var connection = databaseManager.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT id, title, description, enabled FROM mplt_title WHERE id = ?"
             )) {
            statement.setInt(1, id);
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(resultSet));
            }
        }
    }

    public Optional<TitleRecord> findByTitle(String title) throws Exception {
        try (var connection = databaseManager.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT id, title, description, enabled FROM mplt_title WHERE title = ?"
             )) {
            statement.setString(1, title);
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(resultSet));
            }
        }
    }

    public List<TitleRecord> findAll(boolean includeDisabled) throws Exception {
        List<TitleRecord> result = new ArrayList<>();
        String sql = includeDisabled
                ? "SELECT id, title, description, enabled FROM mplt_title ORDER BY id ASC"
                : "SELECT id, title, description, enabled FROM mplt_title WHERE enabled = ? ORDER BY id ASC";
        try (var connection = databaseManager.getConnection();
             var statement = connection.prepareStatement(sql)) {
            if (!includeDisabled) {
                statement.setBoolean(1, true);
            }
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(map(resultSet));
                }
            }
        }
        return result;
    }

    public TitleRecord save(TitleRecord titleRecord) throws Exception {
        if (titleRecord.id() > 0) {
            try (var connection = databaseManager.getConnection();
                 var statement = connection.prepareStatement(
                         "UPDATE mplt_title SET title = ?, description = ?, enabled = ? WHERE id = ?"
                 )) {
                statement.setString(1, titleRecord.title());
                statement.setString(2, titleRecord.description());
                statement.setBoolean(3, titleRecord.enabled());
                statement.setInt(4, titleRecord.id());
                statement.executeUpdate();
            }
            return titleRecord;
        }
        try (var connection = databaseManager.getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO mplt_title (title, description, enabled) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS
             )) {
            statement.setString(1, titleRecord.title());
            statement.setString(2, titleRecord.description());
            statement.setBoolean(3, titleRecord.enabled());
            statement.executeUpdate();
            try (var generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return new TitleRecord(generatedKeys.getInt(1), titleRecord.title(), titleRecord.description(), titleRecord.enabled());
                }
            }
        }
        throw new IllegalStateException("Failed to insert title.");
    }

    private TitleRecord map(java.sql.ResultSet resultSet) throws Exception {
        return new TitleRecord(
                resultSet.getInt("id"),
                resultSet.getString("title"),
                resultSet.getString("description"),
                resultSet.getBoolean("enabled")
        );
    }
}
