package cn.lunadeer.deertitle.database.repository;

import cn.lunadeer.deertitle.database.DatabaseManager;
import cn.lunadeer.deertitle.database.model.DateParts;
import cn.lunadeer.deertitle.database.model.PlayerTitleRecord;

import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PlayerTitleRepository {

    private final DatabaseManager databaseManager;

    public PlayerTitleRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Optional<PlayerTitleRecord> findById(int id) throws Exception {
        try (var connection = databaseManager.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT id, player_uuid, title_id, expire_at_y, expire_at_m, expire_at_d FROM mplt_player_title WHERE id = ?"
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

    public Optional<PlayerTitleRecord> findOwnership(UUID playerUuid, int titleId) throws Exception {
        try (var connection = databaseManager.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT id, player_uuid, title_id, expire_at_y, expire_at_m, expire_at_d FROM mplt_player_title WHERE player_uuid = ? AND title_id = ?"
             )) {
            statement.setString(1, playerUuid.toString());
            statement.setInt(2, titleId);
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(resultSet));
            }
        }
    }

    public List<PlayerTitleRecord> findByPlayer(UUID playerUuid) throws Exception {
        List<PlayerTitleRecord> result = new ArrayList<>();
        try (var connection = databaseManager.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT id, player_uuid, title_id, expire_at_y, expire_at_m, expire_at_d FROM mplt_player_title WHERE player_uuid = ? ORDER BY id ASC"
             )) {
            statement.setString(1, playerUuid.toString());
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(map(resultSet));
                }
            }
        }
        return result;
    }

    public List<PlayerTitleRecord> findExpired(LocalDate today) throws Exception {
        List<PlayerTitleRecord> result = new ArrayList<>();
        try (var connection = databaseManager.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT id, player_uuid, title_id, expire_at_y, expire_at_m, expire_at_d FROM mplt_player_title WHERE expire_at_y >= 0"
             );
             var resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                PlayerTitleRecord record = map(resultSet);
                LocalDate expireAt = record.expireAt().asLocalDate();
                if (expireAt != null && expireAt.isBefore(today)) {
                    result.add(record);
                }
            }
        }
        return result;
    }

    public PlayerTitleRecord save(UUID playerUuid, int titleId, DateParts expireAt) throws Exception {
        Optional<PlayerTitleRecord> existing = findOwnership(playerUuid, titleId);
        if (existing.isPresent()) {
            try (var connection = databaseManager.getConnection();
                 var statement = connection.prepareStatement(
                         "UPDATE mplt_player_title SET expire_at_y = ?, expire_at_m = ?, expire_at_d = ? WHERE id = ?"
                 )) {
                bindDateParts(statement, expireAt, 1);
                statement.setInt(4, existing.get().id());
                statement.executeUpdate();
            }
            return new PlayerTitleRecord(existing.get().id(), playerUuid, titleId, expireAt);
        }
        try (var connection = databaseManager.getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO mplt_player_title (player_uuid, title_id, expire_at_y, expire_at_m, expire_at_d) VALUES (?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS
             )) {
            statement.setString(1, playerUuid.toString());
            statement.setInt(2, titleId);
            bindDateParts(statement, expireAt, 3);
            statement.executeUpdate();
            try (var generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return new PlayerTitleRecord(generatedKeys.getInt(1), playerUuid, titleId, expireAt);
                }
            }
        }
        throw new IllegalStateException("Failed to insert player title.");
    }

    public void deleteById(int id) throws Exception {
        try (var connection = databaseManager.getConnection();
             var statement = connection.prepareStatement("DELETE FROM mplt_player_title WHERE id = ?")) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }

    public void deleteByPlayerAndTitle(UUID playerUuid, int titleId) throws Exception {
        try (var connection = databaseManager.getConnection();
             var statement = connection.prepareStatement("DELETE FROM mplt_player_title WHERE player_uuid = ? AND title_id = ?")) {
            statement.setString(1, playerUuid.toString());
            statement.setInt(2, titleId);
            statement.executeUpdate();
        }
    }

    private PlayerTitleRecord map(java.sql.ResultSet resultSet) throws Exception {
        return new PlayerTitleRecord(
                resultSet.getInt("id"),
                UUID.fromString(resultSet.getString("player_uuid")),
                resultSet.getInt("title_id"),
                new DateParts(
                        resultSet.getInt("expire_at_y"),
                        resultSet.getInt("expire_at_m"),
                        resultSet.getInt("expire_at_d")
                )
        );
    }

    private void bindDateParts(java.sql.PreparedStatement statement, DateParts dateParts, int startIndex) throws Exception {
        DateParts value = dateParts == null ? DateParts.permanent() : dateParts;
        statement.setInt(startIndex, value.year());
        statement.setInt(startIndex + 1, value.month());
        statement.setInt(startIndex + 2, value.day());
    }
}
