package com.fcareer.versioning.model;

import java.util.Map;

/** Bir oyuncunun karşılaştırmaya giren alanları. attrs anahtarı Attr.key (ör. SprintSpeed), playstyles değeri 1=PS, 2=PS+. */
public record VersionSnapshot(Integer overall, Integer potential, String accelerate, String positions, Integer clubId,
                              Map<String, Integer> attrs, Map<String, Integer> playstyles) {
}
