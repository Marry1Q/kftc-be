package com.kopo_team4.auth_backend.domain.account.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 계좌주명 조회 응답 DTO
 * 
 * 계좌번호 조회 결과로 해당 계좌의 예금주명만을 간단히 반환합니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "계좌주명 조회 응답")
public class AccountHolderNameResponse {
    
    /**
     * 계좌주명 (예금주명)
     * 
     * 외부 오픈뱅킹 API에서 조회한 실제 계좌의 소유자 이름입니다.
     */
    @Schema(description = "계좌주명 (예금주명)", example = "김철수")
    private String accountHolderName;
}
