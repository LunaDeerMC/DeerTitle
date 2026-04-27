package cn.lunadeer.deertitle.database.model;

import java.util.UUID;

public record PlayerTitleRecord(int id, UUID playerUuid, int titleId, DateParts expireAt) {
}
