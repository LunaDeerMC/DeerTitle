package cn.lunadeer.deertitle.database.model;

import java.util.UUID;

public record PlayerInfoRecord(UUID uuid, Integer usingTitleId, String lastUseName, double coin) {
}
