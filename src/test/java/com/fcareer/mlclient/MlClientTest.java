package com.fcareer.mlclient;

import com.fcareer.common.exception.DownstreamServiceException;
import com.fcareer.mlclient.client.MlClient;
import com.fcareer.mlclient.model.MlPlayerFeatures;
import com.fcareer.mlclient.model.MlPredictResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MlClientTest {

    private MockRestServiceServer server;
    private MlClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MlClient(builder.build());
    }

    @Test
    void sendsSnakeCaseContractAndParsesPredictions() {
        server.expect(requestTo("http://localhost:8000/ml/predict"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.players[0].player_id").value(5))
                .andExpect(jsonPath("$.players[0].position_group").value("ATT"))
                .andExpect(jsonPath("$.players[0].is_gk").value(false))
                .andRespond(withSuccess("""
                        {"predictions":[{"player_id":5,"potential":82,"value":4500000,"model_version":"xgb-1"}],
                         "fc27_pot_flag":true}""", MediaType.APPLICATION_JSON));

        MlPredictResponse response = client.predict(List.of(MlPlayerFeatures.builder()
                .playerId(5L).age(20).overall(70).positionGroup("ATT").goalkeeper(false).build()));

        assertThat(response.getPredictions()).hasSize(1);
        assertThat(response.getPredictions().get(0).getPotential()).isEqualTo(82);
        assertThat(response.isFc27PotFlag()).isTrue();
        server.verify();
    }

    @Test
    void mapsServiceUnavailableToDownstreamException() {
        server.expect(requestTo("http://localhost:8000/ml/predict")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.predict(List.of(MlPlayerFeatures.builder().playerId(1L).age(20).overall(70).build())))
                .isInstanceOf(DownstreamServiceException.class);
    }
}
