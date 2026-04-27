package cn.lunadeer.deertitle.database.repository;

import cn.lunadeer.deertitle.database.DatabaseManager;
import cn.lunadeer.deertitle.database.model.PlayerInfoRecord;

import java.sql.Statement;
import java.sql.Types;
import java.util.Optional;
import java.util.UUID;

public final class PlayerInfoRepository {

    private final DatabaseManager databaseManager;

    public PlayerInfoRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Optional<PlayerInfoRecord> find(UUID uuid) throws Exception {
        try (var connection = databaseManager.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT uuid, using_title_id, last_use_name, coin_d FROM mplt_player_info WHERE uuid = ?"
             )) {
            statement.setString(1, uuid.toString());
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                Integer usingTitleId = (Integer) resultSet.getObject("using_title_id");
                return Optional.of(new PlayerInfoRecord(
                        UUID.fromString(resultSet.getString("uuid")),
                        usingTitleId,
                        resultSet.getString("last_use_name"),
                        resultSet.getDouble("coin_d")
                ));
            }
        }
    }

    public PlayerInfoRecord findOrCreate(UUID uuid, String lastUseName, double defaultBalance) throws Exception {
        Optional<PlayerInfoRecord> existing = find(uuid);
        if (existing.isPresent()) {
            if (lastUseName != null && !lastUseName.isBlank() && !lastUseName.equals(existing.get().lastUseName())) {
                updateName(uuid, lastUseName);
                return new PlayerInfoRecord(uuid, existing.get().usingTitleId(), lastUseName, existing.get().coin());
            }
            return existing.get();
        }
        try (var connection = databaseManager.getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO mplt_player_info (uuid, using_title_id, last_use_name, coin_d) VALUES (?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS
             )) {
            statement.setString(1, uuid.toString());
            statement.setNull(2, Types.INTEGER);
            statement.setString(3, lastUseName == null || lastUseName.isBlank() ? "unknown" : lastUseName);
            statement.setDouble(4, defaultBalance);
            statement.executeUpdate();
        }
        return new PlayerInfoRecord(uuid, null, lastUseName == null || lastUseName.isBlank() ? "unknown" : lastUseName, defaultBalance);
    }

    public void updateCurrentTitle(UUID uuid, Integer titleId) throws Exception {
        try (var connection = databaseManager.getConnection();
             var statement = connection.prepareStatement("UPDATE mplt_player_info SET using_title_id = ? WHERE uuid = ?")) {
            if (titleId == null) {
                statement.setNull(1, Types.INTEGER);
            } else {
                statement.setInt(1, titleId);
            }
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        }
    }

    public void updateName(UUID uuid, String lastUseName) throws Exception {
        try (var connection = databaseManager.getConnection();
             var statement = connection.prepareStatement("UPDATE mplt_player_info SET last_use_name = ? WHERE uuid = ?")) {
            statement.setString(1, lastUseName);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        }
    }

    public void updateCoin(UUID uuid, double coin) throws Exception {
        try (var connection = databaseManager.getConnection();
             var statement = connection.prepareStatement("UPDATE mplt_player_info SET coin_d = ? WHERE uuid = ?")) {
            statement.setDouble(1, coin);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        }
    }
}
