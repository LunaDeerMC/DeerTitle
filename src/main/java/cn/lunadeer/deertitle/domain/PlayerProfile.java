package cn.lunadeer.deertitle.domain;

import java.util.UUID;

public record PlayerProfile(UUID uuid, String name, Long equippedTitleId, long coins, int customCredits, long revision) {}
