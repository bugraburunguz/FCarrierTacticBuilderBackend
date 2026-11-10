package com.fcareer.recommendation.pipeline.model;

import java.util.List;

/**
 * FCA-6: kariyer önerisinde seçilebilecek ligler (öneri isteğindeki 422 kuralıyla aynı liste). restricted=false: kulübün lig kademesi
 * bilinmiyor, her lig seçilebilir ve leagueIds boştur.
 */
public record ReachableLeaguesResponse(boolean restricted, List<Integer> leagueIds) {
}
