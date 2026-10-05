package com.fcareer.roles.repository;

import com.fcareer.roles.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleRepository extends JpaRepository<RoleEntity, String> {

    List<RoleEntity> findAllByOrderByIdAsc();
}
