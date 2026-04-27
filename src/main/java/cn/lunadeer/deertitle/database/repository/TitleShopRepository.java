package cn.lunadeer.deertitle.database.repository;

import cn.lunadeer.deertitle.database.DatabaseManager;
import cn.lunadeer.deertitle.database.model.DateParts;
import cn.lunadeer.deertitle.database.model.TitleShopRecord;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TitleShopRepository {

    private final DatabaseManager databaseManager;

    public TitleShopRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Optional<TitleShopRecord> findById(int id) throws Exception {
        try (var connection = databaseManager.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT id, title_id, days, amount, sale_end_at_y, sale_end_at_m, sale_end_at_d, price_d FROM mplt_title_shop WHERE id = ?"
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

    public Optional<TitleShopRecord> findByTitleId(int titleId) throws Exception {
        try (var connection = databaseManager.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT id, title_id, days, amount, sale_end_at_y, sale_end_at_m, sale_end_at_d, price_d FROM mplt_title_shop WHERE title_id = ? ORDER BY id DESC"
             )) {
            statement.setInt(1, titleId);
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(resultSet));
            }
        }
    }

    public List<TitleShopRecord> findAll() throws Exception {
        List<TitleShopRecord> result = new ArrayList<>();
        try (var connection = databaseManager.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT id, title_id, days, amount, sale_end_at_y, sale_end_at_m, sale_end_at_d, price_d FROM mplt_title_shop ORDER BY id ASC"
             );
             var resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                result.add(map(resultSet));
            }
        }
        return result;
    }

    public TitleShopRecord save(TitleShopRecord record) throws Exception {
        if (record.id() > 0) {
            try (var connection = databaseManager.getConnection();
                 var statement = connection.prepareStatement(
                         """
                         UPDATE mplt_title_shop
                         SET title_id = ?, days = ?, amount = ?, updated_at = CURRENT_TIMESTAMP, sale_end_at_y = ?, sale_end_at_m = ?, sale_end_at_d = ?, price_d = ?
                         WHERE id = ?
                         """
                 )) {
                statement.setInt(1, record.titleId());
                statement.setInt(2, record.days());
                statement.setInt(3, record.amount());
                bindDateParts(statement, record.saleEndAt(), 4);
                statement.setDouble(7, record.price());
                statement.setInt(8, record.id());
                statement.executeUpdate();
            }
            return record;
        }
        try (var connection = databaseManager.getConnection();
             var statement = connection.prepareStatement(
                     """
                     INSERT INTO mplt_title_shop (title_id, days, amount, sale_end_at_y, sale_end_at_m, sale_end_at_d, price_d)
                     VALUES (?, ?, ?, ?, ?, ?, ?)
                     """,
                     Statement.RETURN_GENERATED_KEYS
             )) {
            statement.setInt(1, record.titleId());
            statement.setInt(2, record.days());
            statement.setInt(3, record.amount());
            bindDateParts(statement, record.saleEndAt(), 4);
            statement.setDouble(7, record.price());
            statement.executeUpdate();
            try (var generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return new TitleShopRecord(generatedKeys.getInt(1), record.titleId(), record.days(), record.amount(), record.saleEndAt(), record.price());
                }
            }
        }
        throw new IllegalStateException("Failed to insert title shop record.");
    }

    public void decrementAmount(int id) throws Exception {
        try (var connection = databaseManager.getConnection();
             var statement = connection.prepareStatement(
                     "UPDATE mplt_title_shop SET amount = amount - 1, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND amount > 0"
             )) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }

    public void delete(int id) throws Exception {
        try (var connection = databaseManager.getConnection();
             var statement = connection.prepareStatement("DELETE FROM mplt_title_shop WHERE id = ?")) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }

    private TitleShopRecord map(java.sql.ResultSet resultSet) throws Exception {
        return new TitleShopRecord(
                resultSet.getInt("id"),
                resultSet.getInt("title_id"),
                resultSet.getInt("days"),
                resultSet.getInt("amount"),
                new DateParts(
                        resultSet.getInt("sale_end_at_y"),
                        resultSet.getInt("sale_end_at_m"),
                        resultSet.getInt("sale_end_at_d")
                ),
                resultSet.getDouble("price_d")
        );
    }

    private void bindDateParts(java.sql.PreparedStatement statement, DateParts dateParts, int startIndex) throws Exception {
        DateParts value = dateParts == null ? DateParts.permanent() : dateParts;
        statement.setInt(startIndex, value.year());
        statement.setInt(startIndex + 1, value.month());
        statement.setInt(startIndex + 2, value.day());
    }
}
