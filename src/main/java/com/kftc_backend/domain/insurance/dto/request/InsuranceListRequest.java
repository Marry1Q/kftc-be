package com.kftc_backend.domain.insurance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "사용자 보험 목록 조회 요청")
public class InsuranceListRequest {

    @Schema(description = "거래고유번호", example = "BANKTRAN123456789012")
    private String bankTranId;

    @Schema(description = "사용자 고유 식별값", example = "U001111111")
    private String userSeqNo;

    @Schema(description = "보험사 표준 코드", example = "436")
    private String bankCodeStd;
} 