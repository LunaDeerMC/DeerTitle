package cn.lunadeer.deertitle.database.repository;

import cn.lunadeer.deertitle.database.DatabaseManager;

public final class RepositoryRegistry {

    private final TitleRepository titles;
    private final PlayerInfoRepository playerInfo;
    private final PlayerTitleRepository playerTitles;
    private final TitleShopRepository titleShop;

    public RepositoryRegistry(DatabaseManager databaseManager) {
        this.titles = new TitleRepository(databaseManager);
        this.playerInfo = new PlayerInfoRepository(databaseManager);
        this.playerTitles = new PlayerTitleRepository(databaseManager);
        this.titleShop = new TitleShopRepository(databaseManager);
    }

    public TitleRepository titles() {
        return titles;
    }

    public PlayerInfoRepository playerInfo() {
        return playerInfo;
    }

    public PlayerTitleRepository playerTitles() {
        return playerTitles;
    }

    public TitleShopRepository titleShop() {
        return titleShop;
    }
}
