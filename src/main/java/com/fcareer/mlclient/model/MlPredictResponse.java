package com.fcareer.mlclient.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MlPredictResponse {
    private List<MlPrediction> predictions;
    @JsonProperty("fc27_pot_flag")
    private boolean fc27PotFlag;
}
