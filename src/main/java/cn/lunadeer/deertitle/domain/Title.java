package cn.lunadeer.deertitle.domain;

import java.util.UUID;

public record Title(long id, String name, String miniMessage, String description, UUID creator, boolean custom, boolean enabled, long version) {}
