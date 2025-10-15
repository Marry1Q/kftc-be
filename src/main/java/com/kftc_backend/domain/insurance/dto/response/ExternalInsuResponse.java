package com.kftc_backend.domain.insurance.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class ExternalInsuResponse {
    private int status;
    private boolean success;
    private String message;
    private int insuCnt;
    private List<ExternalInsuData> data;

    @Getter
    @NoArgsConstructor
    public static class ExternalInsuData {
        private String insuNum;
        @JsonProperty("productName")
        private String productName;
        private String insuType;
        private String insuStatus;
        private String bankCode;
        private String insuranceCompany;
        private String issueDate;
        private String expDate;
    }
} 