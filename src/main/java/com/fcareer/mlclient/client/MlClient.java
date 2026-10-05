package com.fcareer.mlclient.client;

import com.fcareer.common.exception.DownstreamServiceException;
import com.fcareer.mlclient.model.MlPlayerFeatures;
import com.fcareer.mlclient.model.MlPredictRequest;
import com.fcareer.mlclient.model.MlPredictResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Objects;

import static com.fcareer.mlclient.configuration.MlClientConfiguration.ML_REST_CLIENT;

@Slf4j
@Component
public class MlClient {

    private static final String ML_SERVICE = "ml-service";

    private final RestClient restClient;

    public MlClient(@Qualifier(ML_REST_CLIENT) RestClient restClient) {
        this.restClient = restClient;
    }

    public MlPredictResponse predict(List<MlPlayerFeatures> players) {
        try {
            MlPredictResponse response = restClient.post()
                    .uri("/ml/predict")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new MlPredictRequest(players))
                    .retrieve()
                    .body(MlPredictResponse.class);
            if (Objects.isNull(response) || Objects.isNull(response.getPredictions())) {
                throw new DownstreamServiceException(ML_SERVICE);
            }
            return response;
        } catch (RestClientException e) {
            log.error("ML service call failed batchSize : {}", players.size(), e);
            throw new DownstreamServiceException(ML_SERVICE);
        }
    }
}
