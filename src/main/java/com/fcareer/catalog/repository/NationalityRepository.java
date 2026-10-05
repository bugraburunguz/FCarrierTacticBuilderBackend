package com.fcareer.catalog.repository;

import com.fcareer.catalog.entity.NationalityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NationalityRepository extends JpaRepository<NationalityEntity, Integer> {

    Optional<NationalityEntity> findByName(String name);
}
