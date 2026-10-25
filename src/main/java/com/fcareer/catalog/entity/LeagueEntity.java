package com.fcareer.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "league")
public class LeagueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String name;

    private String country;

    private Short tier;

    /** Yalnız okuma (yazımı JDBC içe aktarma yapar): çözülmemiş lig adının yedeği "Lig #id" için. */
    @Column(name = "ea_league_id", insertable = false, updatable = false)
    private Integer eaLeagueId;
}
