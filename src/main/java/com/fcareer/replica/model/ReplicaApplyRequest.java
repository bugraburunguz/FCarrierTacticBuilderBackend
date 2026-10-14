package com.fcareer.replica.model;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Kadro kaynağı: careerId (kariyer kadrosu) YA DA playerIds (UT kulüp kartları; katalog kimlikleri) YA DA hiçbiri (katalogdan örnek). */
@Getter
@Setter
public class ReplicaApplyRequest {

    private Long careerId;

    @Size(max = 200)
    private List<Long> playerIds;
}
