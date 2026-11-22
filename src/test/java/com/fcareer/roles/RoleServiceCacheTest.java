package com.fcareer.roles;

import com.fcareer.common.cache.ReferenceCache;
import com.fcareer.common.configuration.CacheProperties;
import com.fcareer.common.exception.NotFoundException;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.roles.entity.RoleEntity;
import com.fcareer.roles.mapper.RoleMapper;
import com.fcareer.roles.repository.RoleRepository;
import com.fcareer.roles.service.RoleService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleServiceCacheTest {

    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final RoleService roleService = new RoleService(roleRepository, roleMapper, mock(ReferenceCache.class), mock(CacheProperties.class));

    @Test
    void getFitRoleReadsTheCachedRoleMapInsteadOfQueryingPerCall() {
        RoleEntity entity = new RoleEntity();
        when(roleRepository.findAll()).thenReturn(List.of(entity));
        when(roleMapper.toFitRole(any())).thenReturn(FitRole.builder().id("cb-defend").name("Defend").build());

        FitRole first = roleService.getFitRole("cb-defend");
        FitRole second = roleService.getFitRole("cb-defend");

        assertThat(first.getId()).isEqualTo("cb-defend");
        assertThat(second).isSameAs(first);
        verify(roleRepository, never()).findById(anyString());
        verify(roleRepository, times(1)).findAll();
    }

    @Test
    void unknownRoleStillThrowsNotFound() {
        when(roleRepository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> roleService.getFitRole("missing")).isInstanceOf(NotFoundException.class);
        verify(roleRepository, never()).findById(anyString());
    }
}
