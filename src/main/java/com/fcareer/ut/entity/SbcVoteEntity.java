package com.fcareer.ut.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "sbc_vote")
@EntityListeners(AuditingEntityListener.class)
public class SbcVoteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long setId;

    private Short vote;

    @LastModifiedDate
    @Column(columnDefinition = "timestamptz")
    private Instant updatedAt;
}
