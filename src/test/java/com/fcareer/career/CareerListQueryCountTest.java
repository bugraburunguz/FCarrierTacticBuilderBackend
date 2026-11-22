package com.fcareer.career;

import com.fcareer.career.entity.CareerEntity;
import com.fcareer.career.mapper.CareerMapper;
import com.fcareer.career.repository.CareerRepository;
import com.fcareer.career.repository.UserPlayerStateRepository;
import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.entity.ClubEntity;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.catalog.squad.SquadVersionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareerListQueryCountTest {

    private static final long USER = 5L;

    @Mock
    private CareerRepository careerRepository;
    @Mock
    private UserPlayerStateRepository userPlayerStateRepository;
    @Mock
    private ClubRepository clubRepository;
    @Mock
    private CareerMapper careerMapper;
    @Mock
    private SquadVersionService squadVersionService;
    @InjectMocks
    private CareerService careerService;

    @Test
    void listLoadsClubsAndSquadSizesInOneQueryEach() {
        when(careerRepository.findByUserIdOrderByIdDesc(USER)).thenReturn(List.of(career(3L, 10), career(2L, 11), career(1L, 10)));
        when(squadVersionService.findCareerVersions(anyList())).thenReturn(Map.of());
        when(clubRepository.findAllById(any())).thenReturn(List.of(club(10, "Celta"), club(11, "Port Vale")));
        when(userPlayerStateRepository.countByCareerIds(any())).thenReturn(List.of(size(3L, 25), size(1L, 2)));

        careerService.list(USER);

        verify(clubRepository, times(1)).findAllById(any());
        verify(clubRepository, never()).findById(anyInt());
        verify(userPlayerStateRepository, times(1)).countByCareerIds(any());
        verify(userPlayerStateRepository, never()).findByCareerIdOrderByIdAsc(anyLong());
        verify(careerMapper).toResponse(any(CareerEntity.class), eq("Celta"), eq((short) 0), eq(25), isNull());
        verify(careerMapper).toResponse(any(CareerEntity.class), eq("Port Vale"), eq((short) 0), eq(0), isNull());
        verify(careerMapper).toResponse(any(CareerEntity.class), eq("Celta"), eq((short) 0), eq(2), isNull());
    }

    @Test
    void getCountsTheSquadWithoutLoadingItsRows() {
        CareerEntity career = career(7L, 10);
        when(careerRepository.findById(7L)).thenReturn(Optional.of(career));
        when(squadVersionService.findCareerVersions(anyList())).thenReturn(Map.of());
        when(clubRepository.findById(10)).thenReturn(Optional.of(club(10, "Celta")));
        when(userPlayerStateRepository.countByCareerId(7L)).thenReturn(18L);

        careerService.get(USER, 7L);

        verify(userPlayerStateRepository, never()).findByCareerIdOrderByIdAsc(anyLong());
        verify(careerMapper).toResponse(eq(career), eq("Celta"), eq((short) 0), eq(18), isNull());
    }

    private static CareerEntity career(long id, int clubId) {
        CareerEntity career = new CareerEntity();
        career.setId(id);
        career.setUserId(USER);
        career.setClubId(clubId);
        career.setGameVersionId((short) 27);
        career.setSquadVersionId(1L);
        return career;
    }

    private static ClubEntity club(int id, String name) {
        ClubEntity club = new ClubEntity();
        club.setId(id);
        club.setName(name);
        club.setGender((short) 0);
        return club;
    }

    private static UserPlayerStateRepository.CareerSquadSize size(long careerId, long size) {
        return new UserPlayerStateRepository.CareerSquadSize() {
            @Override
            public Long getCareerId() {
                return careerId;
            }

            @Override
            public long getSize() {
                return size;
            }
        };
    }
}
