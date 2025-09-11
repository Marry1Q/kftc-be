package com.kopo_team4.auth_backend.domain.insurance.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "사용자 보험 목록 조회 응답")
public class InsuranceListResponse {

    @Schema(description = "보험 계약 건수")
    private int insuCnt;

    @JsonProperty("insuList")
    @Schema(description = "보험 목록")
    private List<InsuInfo> insuList;

    @Getter
    @Builder
    @Schema(description = "보험 상세 정보")
    public static class InsuInfo {
        @Schema(description = "보험계약번호", example = "INSU1234567890123456")
        private String insuNum;
        @Schema(description = "보험상품명", example = "자동차종합보험")
        private String prodName;
        @Schema(description = "보험종류", example = "01")
        private String insuType;
        @Schema(description = "보험사명", example = "교보생명")
        private String insuranceCompany;
        @Schema(description = "계약상태", example = "02")
        private String insuStatus;
        @Schema(description = "계약시작일자", example = "20200101")
        private String issueDate;
        @Schema(description = "계약종료일자", example = "20250101")
        private String expDate;
    }
} 