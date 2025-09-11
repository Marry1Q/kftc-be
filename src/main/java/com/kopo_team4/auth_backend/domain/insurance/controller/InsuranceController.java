package com.kopo_team4.auth_backend.domain.insurance.controller;

import com.kopo_team4.auth_backend.domain.insurance.dto.request.InsuranceListRequest;
import com.kopo_team4.auth_backend.domain.insurance.dto.response.InsuranceListResponse;
import com.kopo_team4.auth_backend.domain.insurance.service.InsuranceService;
import com.kopo_team4.auth_backend.global.dto.ApiResponse;
import com.kopo_team4.auth_backend.global.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v2.0/insurances")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "보험 조회", description = "보험 관련 조회 API")
public class InsuranceController {

    private final InsuranceService insuranceService;
    private final JwtUtil jwtUtil;

    @PostMapping
    @Operation(summary = "사용자 보험 목록 조회", description = "사용자의 보험 계약 목록을 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<InsuranceListResponse>> getInsuranceList(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authorization,
            @RequestBody InsuranceListRequest request) {

        try {
            String token = jwtUtil.extractTokenFromHeader(authorization);
            if (!jwtUtil.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("유효하지 않은 토큰입니다."));
            }

            InsuranceListResponse response = insuranceService.getInsuranceList(request);
            return ResponseEntity.ok(ApiResponse.success("사용자 보험 목록이 성공적으로 조회되었습니다.", response));

        } catch (IllegalArgumentException e) {
            log.warn("보험 목록 조회 실패 - 잘못된 요청: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("보험 목록 조회 실패 - 시스템 오류: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("보험 목록 조회 중 오류가 발생했습니다."));
        }
    }
} 