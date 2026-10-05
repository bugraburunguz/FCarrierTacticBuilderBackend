package com.fcareer.mlclient.model;

import java.util.List;

public record MlPredictRequest(List<MlPlayerFeatures> players) {
}
