package cn.lunadeer.deertitle.domain;

import java.util.UUID;

public record ImportPlayer(UUID uuid, String name, Long coins, String equippedLegacyTitleId) {}
