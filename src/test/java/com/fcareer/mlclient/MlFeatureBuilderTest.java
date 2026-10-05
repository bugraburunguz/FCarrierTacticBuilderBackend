package com.fcareer.mlclient;

import com.fcareer.catalog.entity.BasePlayerEntity;
import com.fcareer.catalog.entity.PlayerRatingEntity;
import com.fcareer.catalog.mapper.RatingAttrAccessor;
import com.fcareer.mlclient.configuration.MlProperties;
import com.fcareer.mlclient.model.MlPlayerFeatures;
import com.fcareer.mlclient.service.MlFeatureBuilder;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MlFeatureBuilderTest {

    private final RatingAttrAccessor accessor = new RatingAttrAccessor();

    private MlPlayerFeatures build(LocalDate reference, LocalDate dob, String position, short sprint, short acceleration) {
        MlProperties properties = new MlProperties();
        properties.setAgeReferenceDate(reference);
        BasePlayerEntity base = new BasePlayerEntity();
        base.setId(1L);
        base.setDob(dob);
        PlayerRatingEntity rating = new PlayerRatingEntity();
        rating.setOverall((short) 80);
        rating.setPositions(List.of(position));
        rating.setSprintSpeed(sprint);
        rating.setAcceleration(acceleration);
        return new MlFeatureBuilder(accessor, properties).build(base, rating, null);
    }

    @Test
    void ageIsComputedAtTheConfiguredReferenceDateNotToday() {
        MlPlayerFeatures features = build(LocalDate.of(2026, 10, 5), LocalDate.of(1998, 12, 20), "ST", (short) 96, (short) 97);

        assertThat(features.getAge()).isEqualTo(27);
    }

    @Test
    void birthdayOnReferenceDateCountsAsTheNewAge() {
        assertThat(build(LocalDate.of(2026, 10, 5), LocalDate.of(2000, 10, 5), "CM", (short) 70, (short) 70).getAge()).isEqualTo(26);
        assertThat(build(LocalDate.of(2026, 10, 5), LocalDate.of(2000, 10, 6), "CM", (short) 70, (short) 70).getAge()).isEqualTo(25);
    }

    @Test
    void usesFirstPositionForGroupAndMeansAttributeGroups() {
        MlPlayerFeatures features = build(LocalDate.of(2026, 10, 5), LocalDate.of(1998, 12, 20), "LWB", (short) 90, (short) 80);

        assertThat(features.getPositionGroup()).isEqualTo("DEF");
        assertThat(features.getGoalkeeper()).isFalse();
        assertThat(features.getPace()).isEqualTo(85.0);
    }

    @Test
    void goalkeepersAreFlagged() {
        assertThat(build(LocalDate.of(2026, 10, 5), LocalDate.of(1990, 1, 1), "GK", (short) 50, (short) 50).getGoalkeeper()).isTrue();
    }
}
