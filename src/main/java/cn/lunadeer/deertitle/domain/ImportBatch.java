package cn.lunadeer.deertitle.domain;

import java.util.List;

public record ImportBatch(String sourceKey, Currency currency, List<ImportTitle> titles, List<ImportPlayer> players, List<ImportOwnership> ownerships, List<ImportListing> listings) {
    public ImportBatch {
        titles = List.copyOf(titles);
        players = List.copyOf(players);
        ownerships = List.copyOf(ownerships);
        listings = List.copyOf(listings);
    }
}
