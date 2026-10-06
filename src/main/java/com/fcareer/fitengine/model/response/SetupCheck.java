package com.fcareer.fitengine.model.response;

import com.fcareer.fitengine.model.WeaponState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetupCheck {
    private String title;
    private WeaponState status;
    private String detail;
    private List<Candidate> players;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Candidate {
        private Long id;
        private String name;
        private String position;
        private int value;
        private boolean ok;
    }
}
