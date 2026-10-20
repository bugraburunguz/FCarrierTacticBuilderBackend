package com.fcareer.roles.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fcareer.common.configuration.CacheProperties;
import com.fcareer.common.exception.NotFoundException;
import com.fcareer.common.cache.ReferenceCache;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.roles.mapper.RoleMapper;
import com.fcareer.roles.model.response.RoleResponse;
import com.fcareer.roles.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.fcareer.common.constants.KeyConstants.CACHE_ROLES;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService {

    private static final String ROLE = "role";

    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;
    private final ReferenceCache referenceCache;
    private final CacheProperties cacheProperties;

    public List<RoleResponse> findAll() {
        return referenceCache.getOrLoad(CACHE_ROLES, new TypeReference<>() {
                }, cacheProperties.getReferenceTtl(),
                () -> roleRepository.findAllByOrderByIdAsc().stream().map(roleMapper::toResponse).toList());
    }

    public FitRole getFitRole(String roleId) {
        return roleRepository.findById(roleId)
                .map(roleMapper::toFitRole)
                .orElseThrow(() -> new NotFoundException(ROLE, roleId));
    }

    private final com.fcareer.common.cache.TtlMemo<Map<String, FitRole>> fitRoles = new com.fcareer.common.cache.TtlMemo<>(java.time.Duration.ofMinutes(10),
            this::loadFitRoles);

    /** Rol kütüphanesi nadir değişir; her slot çağrısında yeniden yüklenmesin diye 10 dk bellekte tutulur (BUG-17). */
    public Map<String, FitRole> fitRolesById() {
        return fitRoles.get();
    }

    private Map<String, FitRole> loadFitRoles() {
        return roleRepository.findAll().stream().map(roleMapper::toFitRole).collect(Collectors.toMap(FitRole::getId, Function.identity()));
    }
}
