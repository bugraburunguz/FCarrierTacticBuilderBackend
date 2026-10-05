package com.fcareer.roles.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "behavior_tag")
public class BehaviorTagEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String position;

    @Column(nullable = false)
    private String labelTr;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> weaponAttrs;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> playstyleWeapons;

    @Column(nullable = false)
    private Short expectedMin;

    private String roleVote;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> conflicts;

    private String nlgTr;

    private String accelerateHint;

    @Column(nullable = false)
    private Short sortOrder;
}
