package cn.lunadeer.deertitle.database.model;

public record TitleShopRecord(int id, int titleId, int days, int amount, DateParts saleEndAt, double price) {
}
