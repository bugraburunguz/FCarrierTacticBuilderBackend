package com.fcareer.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    /** KARIYER-UX: "yok" yanıtı (ör. ana taktik yok) gövdede `data: null` olarak görünür; alan hiç düşmez. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private T data;
    private String message;
    private List<ApiError> error;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    public static ApiResponse<Void> fail(String message, String code) {
        return ApiResponse.<Void>builder()
                .success(false)
                .message(message)
                .error(List.of(new ApiError(code, message)))
                .build();
    }

    public static ApiResponse<Void> fail(String message, List<ApiError> errors) {
        return ApiResponse.<Void>builder()
                .success(false)
                .message(message)
                .error(errors)
                .build();
    }
}
