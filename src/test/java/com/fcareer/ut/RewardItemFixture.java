package com.fcareer.ut;

import java.util.List;

/**
 * V3-05c-c: ödül itemData'sı için SENTETİK fixture. Alan adları EA yanıtındakiyle aynı, değerler uydurma (gerçek yakalamadan türetilmedi,
 * kişisel/örnek kimlik yok). İzinli 9 alan + alınmaması gereken alanlar + bilinmeyen yeni bir alan.
 */
final class RewardItemFixture {

    static final List<String> ALLOWED = List.of("resourceId", "assetId", "rating", "preferredPosition", "possiblePositions", "rareflag", "nation", "leagueId", "teamid");
    static final List<String> FORBIDDEN = List.of("\"id\"", "owners", "timestamp", "lastSalePrice", "contract", "lifetimeStats", "statsList", "loyaltyBonus", "loansInfo",
            "injuryType", "injuryGames", "pile", "discardValue", "untradeable", "futureNewField", "attributeArray", "marketDataMinPrice");

    static final String ITEM_DATA = """
            {"id":900000000001,"resourceId":50000777,"assetId":777,"rating":88,"preferredPosition":"ST","possiblePositions":["ST","CF"],"rareflag":3,
             "nation":901,"leagueId":902,"teamid":903,"owners":4,"timestamp":1000,"lastSalePrice":12345,"contract":7,"lifetimeStats":[{"index":0,"value":3}],
             "statsList":[{"index":1,"value":2}],"loyaltyBonus":1,"loansInfo":{"loanValue":1},"injuryType":"none","injuryGames":0,"pile":5,"discardValue":10,
             "untradeable":true,"futureNewField":"x","attributeArray":[90,80,70,60,50,40],"marketDataMinPrice":100,"itemType":"player"}
            """;

    private RewardItemFixture() {
    }
}
