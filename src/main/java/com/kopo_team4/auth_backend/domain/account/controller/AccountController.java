package com.kopo_team4.auth_backend.domain.account.controller;

import com.kopo_team4.auth_backend.domain.account.dto.request.AccountDetailsRequest;
import com.kopo_team4.auth_backend.domain.account.dto.request.AccountInfoRequest;
import com.kopo_team4.auth_backend.domain.account.dto.request.BalanceRequest;
import com.kopo_team4.auth_backend.domain.account.dto.request.TransactionListRequest;
import com.kopo_team4.auth_backend.domain.account.dto.request.WithdrawRequest;
import com.kopo_team4.auth_backend.domain.account.dto.request.DepositRequest;
import com.kopo_team4.auth_backend.domain.account.dto.request.IrpTransferRequest;
import com.kopo_team4.auth_backend.domain.account.dto.request.AccountHolderNameRequest;
import com.kopo_team4.auth_backend.domain.account.dto.response.AccountDetailInfoResponse;
import com.kopo_team4.auth_backend.domain.account.dto.response.AccountInfoResponse;
import com.kopo_team4.auth_backend.domain.account.dto.response.BalanceResponse;
import com.kopo_team4.auth_backend.domain.account.dto.response.TransactionListResponse;
import com.kopo_team4.auth_backend.domain.account.dto.response.WithdrawResponse;
import com.kopo_team4.auth_backend.domain.account.dto.response.DepositResponse;
import com.kopo_team4.auth_backend.domain.account.dto.response.IrpTransferResponse;
import com.kopo_team4.auth_backend.domain.account.dto.response.AccountHolderNameResponse;
import com.kopo_team4.auth_backend.domain.account.service.AccountService;
import com.kopo_team4.auth_backend.global.dto.ApiResponse;
import com.kopo_team4.auth_backend.global.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMethod;

@RestController
@RequestMapping("/v2.0")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "계좌 관리", description = "계좌 관련 조회 API")
public class AccountController {

    private final AccountService accountService;
    private final JwtUtil jwtUtil;

    /**
     * 계좌통합조회 API
     */
    @Operation(
            summary = "계좌통합조회",
            description = "사용자의 모든 계좌 정보를 통합 조회합니다. " +
                    "금융기관 업권별로 조회가 가능하며, 최대 30건까지 조회할 수 있습니다.",
            tags = {"계좌 관리"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "계좌통합조회 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 (필수 파라미터 누락, 잘못된 형식 등)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 (유효하지 않은 토큰)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @PostMapping("/accountinfo/num_list")
    public ResponseEntity<ApiResponse<AccountInfoResponse>> getAccountList(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "계좌통합조회 요청 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = AccountInfoRequest.class))
            )
            @RequestBody AccountInfoRequest request) {

        try {
            log.info("계좌통합조회 요청 수신");

            // 1. Authorization 헤더 존재 여부 확인
            if (authorization == null || authorization.trim().isEmpty()) {
                log.warn("Authorization 헤더가 누락됨");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Authorization 헤더가 필요합니다. 'Authorization: Bearer {토큰}' 형식으로 요청해주세요."));
            }

            // 2. Authorization 헤더에서 토큰 추출
            String authAccessToken = jwtUtil.extractTokenFromHeader(authorization);

            // 3. JWT 토큰 유효성 검증
            if (!jwtUtil.validateToken(authAccessToken)) {
                log.warn("유효하지 않은 토큰");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("유효하지 않은 토큰입니다."));
            }

            // 4. JWT 토큰에서 Client ID 추출 (로깅용)
            String clientId = jwtUtil.extractClientId(authAccessToken);
            String scope = jwtUtil.extractScope(authAccessToken);
            log.info("토큰 인증 성공 - clientId: {}, scope: {}", clientId, scope);

            // 5. 계좌통합조회 처리
            AccountInfoResponse response = accountService.getAccountList(request);

            log.info("계좌통합조회 성공 - 조회 건수: {}", response.getResList().size());

            return ResponseEntity.ok(
                    ApiResponse.success("계좌통합조회가 성공적으로 완료되었습니다.", response)
            );

        } catch (IllegalArgumentException e) {
            log.warn("계좌통합조회 실패 - 잘못된 요청: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("계좌통합조회 실패 - 시스템 오류: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("계좌통합조회 중 시스템 오류가 발생했습니다."));
        }
    }

    @PostMapping("/account/info")
    @Operation(summary = "계좌 상세 정보 조회", description = "토큰과 계좌 정보를 받아 외부 기관으로부터 상세 정보를 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<AccountDetailInfoResponse>> getAccountDetail(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody AccountDetailsRequest request) {

        try {
            String token = jwtUtil.extractTokenFromHeader(authorizationHeader);
            AccountDetailInfoResponse response = accountService.getAccountDetailInfo(token, request);
            return ResponseEntity.ok(ApiResponse.success("계좌 상세 정보 조회가 성공적으로 완료되었습니다.", response));
        } catch (IllegalArgumentException e) {
            log.warn("Account detail info request failed - Invalid argument: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("Account detail info request failed - Configuration error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("서버 설정 오류가 발생했습니다."));
        } catch (Exception e) {
            log.error("Account detail info request failed - Unexpected error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("계좌 상세 정보 조회 중 오류가 발생했습니다."));
        }
    }

    @PostMapping("/account/balance/acnt_num")
    @Operation(summary = "계좌 잔액 조회", description = "사용자 계좌의 잔액을 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<BalanceResponse>> getAccountBalance(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authorization,
            @RequestBody BalanceRequest request) {
        try {
            String token = jwtUtil.extractTokenFromHeader(authorization);
            if (!jwtUtil.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("유효하지 않은 토큰입니다."));
            }

            BalanceResponse response = accountService.getAccountBalance(request);
            return ResponseEntity.ok(ApiResponse.success("계좌 잔액 조회가 성공적으로 완료되었습니다.", response));

        } catch (IllegalArgumentException e) {
            log.warn("계좌 잔액 조회 실패 - 잘못된 요청: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("외부 API 호출 오류: " + e.getResponseBodyAsString(), e);
            return ResponseEntity.status(e.getStatusCode()).body(ApiResponse.error("외부 기관 통신 오류: " + e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.error("계좌 잔액 조회 실패 - 시스템 오류: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("계좌 잔액 조회 중 오류가 발생했습니다."));
        }
    }

    /**
     * 거래내역조회 API (v2.0)
     */
    @RequestMapping(value = "/account/transaction_list/acnt_num", method = RequestMethod.POST)
    @Operation(
            summary = "거래내역조회",
            description = "사용자 계좌의 거래내역을 조회합니다.",
            tags = {"계좌 관리"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "거래내역조회 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 (필수 파라미터 누락, 잘못된 형식 등)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 (유효하지 않은 토큰)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<TransactionListResponse>> getTransactionList(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authorization,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "거래내역조회 요청 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = TransactionListRequest.class))
            )
            @RequestBody TransactionListRequest request) {

        try {
            log.info("거래내역조회 요청 수신 - accountNum: {}", request.getAccountNum());

            // 1. Authorization 헤더 토큰 추출 및 검증
            String token = jwtUtil.extractTokenFromHeader(authorization);
            if (!jwtUtil.validateToken(token)) {
                log.warn("유효하지 않은 토큰");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("유효하지 않은 토큰입니다."));
            }

            // 2. JWT 토큰에서 Client ID 추출 (로깅용)
            String clientId = jwtUtil.extractClientId(token);
            String scope = jwtUtil.extractScope(token);
            log.info("토큰 인증 성공 - clientId: {}, scope: {}", clientId, scope);

            // 3. 거래내역조회 처리
            TransactionListResponse response = accountService.getTransactionList(request);

            log.info("거래내역조회 성공 - 조회 건수: {}", 
                    response.getResList() != null ? response.getResList().size() : 0);

            return ResponseEntity.ok(
                    ApiResponse.success("거래내역조회가 성공적으로 완료되었습니다.", response)
            );

        } catch (IllegalArgumentException e) {
            log.warn("거래내역조회 실패 - 잘못된 요청: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("외부 API 호출 오류: " + e.getResponseBodyAsString(), e);
            return ResponseEntity.status(e.getStatusCode())
                    .body(ApiResponse.error("외부 기관 통신 오류: " + e.getResponseBodyAsString()));

        } catch (Exception e) {
            log.error("거래내역조회 실패 - 시스템 오류: {}", e.getMessage(), e);
                         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                     .body(ApiResponse.error("거래내역조회 중 시스템 오류가 발생했습니다."));
         }
     }

    /**
     * 출금이체 API (v2.0)
     */
    @RequestMapping(value = "/transfer/withdraw/acnt_num", method = RequestMethod.POST)
    @Operation(
            summary = "출금이체",
            description = "사용자 계좌에서 출금이체를 수행합니다.",
            tags = {"계좌 관리"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "출금이체 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 (필수 파라미터 누락, 잘못된 형식 등)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 (유효하지 않은 토큰)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<WithdrawResponse>> withdraw(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authorization,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "출금이체 요청 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = WithdrawRequest.class))
            )
            @RequestBody WithdrawRequest request) {

        try {
            log.info("=====================================================");
            log.info("🏧 [AUTH-BACKEND] 출금이체 요청 수신");
            log.info("=====================================================");
            log.info("📤 요청 데이터:");
            log.info("  ├─ 출금계좌번호: {}", request.getWdAccountNum());
            log.info("  ├─ 거래금액: {}", request.getTranAmt());
            log.info("  ├─ 은행거래ID: {}", request.getBankTranId());
            log.info("  ├─ 거래시간: {}", request.getTranDtime());
            log.info("  ├─ 사용자번호: {}", request.getUserSeqNo());
            log.info("  ├─ 출금은행코드: {}", request.getWdBankCodeStd());
            log.info("  └─ 요청자명: {}", request.getReqClientName());
            log.info("🔑 토큰: {}...", authorization != null ? authorization.substring(0, Math.min(30, authorization.length())) : "null");
            log.info("⏰ 요청 시간: {}", java.time.LocalDateTime.now());
            log.info("=====================================================");

            // 1. Authorization 헤더 토큰 추출 및 검증
            String token = jwtUtil.extractTokenFromHeader(authorization);
            if (!jwtUtil.validateToken(token)) {
                log.warn("유효하지 않은 토큰");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("유효하지 않은 토큰입니다."));
            }

            // 2. JWT 토큰에서 Client ID 추출 (로깅용)
            String clientId = jwtUtil.extractClientId(token);
            String scope = jwtUtil.extractScope(token);
            log.info("토큰 인증 성공 - clientId: {}, scope: {}", clientId, scope);

            // 3. 출금이체 처리
            WithdrawResponse response = accountService.withdraw(request);

            log.info("=====================================================");
            log.info("✅ [AUTH-BACKEND] 출금이체 처리 성공");
            log.info("=====================================================");
            log.info("📥 응답 데이터:");
            log.info("  ├─ 은행거래ID: {}", response.getBankTranId());
            log.info("  ├─ 계좌번호: {}", response.getAccountNum());
            log.info("  ├─ 거래금액: {}", response.getTranAmt());
            log.info("  ├─ 은행명: {}", response.getBankName());
            log.info("  └─ 예금주명: {}", response.getAccountHolderName());
            log.info("⏰ 응답 시간: {}", java.time.LocalDateTime.now());
            log.info("=====================================================");

            return ResponseEntity.ok(
                    ApiResponse.success("출금이체가 성공적으로 완료되었습니다.", response)
            );

        } catch (IllegalArgumentException e) {
            log.warn("출금이체 실패 - 잘못된 요청: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("외부 API 호출 오류: " + e.getResponseBodyAsString(), e);
            return ResponseEntity.status(e.getStatusCode())
                    .body(ApiResponse.error("외부 기관 통신 오류: " + e.getResponseBodyAsString()));

        } catch (Exception e) {
            log.error("출금이체 실패 - 시스템 오류: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("출금이체 중 시스템 오류가 발생했습니다."));
        }
    }

    /**
     * 입금이체 API (v2.0)
     */
    @RequestMapping(value = "/transfer/deposit/acnt_num", method = RequestMethod.POST)
    @Operation(
            summary = "입금이체",
            description = "계좌로 입금이체를 수행합니다.",
            tags = {"계좌 관리"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "입금이체 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 (필수 파라미터 누락, 잘못된 형식 등)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 (유효하지 않은 토큰)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<DepositResponse>> deposit(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authorization,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "입금이체 요청 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = DepositRequest.class))
            )
            @RequestBody DepositRequest request) {

        try {
            log.info("=====================================================");
            log.info("🏦 [AUTH-BACKEND] 입금이체 요청 수신");
            log.info("=====================================================");
            log.info("📤 요청 데이터:");
            log.info("  ├─ 입금계좌번호: {}", request.getAccountNum());
            log.info("  ├─ 거래금액: {}", request.getTranAmt());
            log.info("  ├─ 은행거래ID: {}", request.getBankTranId());
            log.info("  ├─ 거래시간: {}", request.getTranDtime());
            log.info("  ├─ 은행코드: {}", request.getBankCodeStd());
            log.info("  ├─ 예금주명: {}", request.getAccountHolderName());
            log.info("  └─ 요청고객번호: {}", request.getReqClientNum());
            log.info("🔑 토큰: {}...", authorization != null ? authorization.substring(0, Math.min(30, authorization.length())) : "null");
            log.info("⏰ 요청 시간: {}", java.time.LocalDateTime.now());
            log.info("=====================================================");

            // 1. Authorization 헤더 토큰 추출 및 검증
            String token = jwtUtil.extractTokenFromHeader(authorization);
            if (!jwtUtil.validateToken(token)) {
                log.warn("유효하지 않은 토큰");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("유효하지 않은 토큰입니다."));
            }

            // 2. JWT 토큰에서 Client ID 추출 (로깅용)
            String clientId = jwtUtil.extractClientId(token);
            String scope = jwtUtil.extractScope(token);
            log.info("토큰 인증 성공 - clientId: {}, scope: {}", clientId, scope);

            // 3. 입금이체 처리
            DepositResponse response = accountService.deposit(request);

            log.info("=====================================================");
            log.info("✅ [AUTH-BACKEND] 입금이체 처리 성공");
            log.info("=====================================================");
            log.info("📥 응답 데이터:");
            log.info("  ├─ 은행거래ID: {}", response.getBankTranId());
            log.info("  ├─ 계좌번호: {}", response.getAccountNum());
            log.info("  ├─ 거래금액: {}", response.getTranAmt());
            log.info("  ├─ 은행명: {}", response.getBankName());
            log.info("  ├─ 예금주명: {}", response.getAccountHolderName());
            log.info("  └─ 출금은행거래ID: {}", response.getWithdrawBankTranId());
            log.info("⏰ 응답 시간: {}", java.time.LocalDateTime.now());
            log.info("=====================================================");

            return ResponseEntity.ok(
                    ApiResponse.success("입금이체가 성공적으로 완료되었습니다.", response)
            );

        } catch (IllegalArgumentException e) {
            log.warn("입금이체 실패 - 잘못된 요청: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("외부 API 호출 오류: " + e.getResponseBodyAsString(), e);
            return ResponseEntity.status(e.getStatusCode())
                    .body(ApiResponse.error("외부 기관 통신 오류: " + e.getResponseBodyAsString()));

        } catch (Exception e) {
            log.error("입금이체 실패 - 시스템 오류: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("입금이체 중 시스템 오류가 발생했습니다."));
        }
    }

    /**
     * IRP 실물이전 API (v2.0)
     */
    @RequestMapping(value = "/retirement/transfer", method = RequestMethod.POST)
    @Operation(
            summary = "IRP 실물이전",
            description = "퇴직연금 IRP 계좌의 실물이전을 수행합니다.",
            tags = {"계좌 관리"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "IRP 실물이전 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 (필수 파라미터 누락, 잘못된 형식 등)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 (유효하지 않은 토큰)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<IrpTransferResponse>> irpTransfer(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authorization,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "IRP 실물이전 요청 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = IrpTransferRequest.class))
            )
            @RequestBody IrpTransferRequest request) {

        try {
            log.info("IRP 실물이전 요청 수신 - userSeqNo: {}, wdAccountNum: {}, rsvAccountNum: {}", 
                    request.getUserSeqNo(), request.getWdAccountNum(), request.getRsvAccountNum());

            // 1. Authorization 헤더 토큰 추출 및 검증
            String token = jwtUtil.extractTokenFromHeader(authorization);
            if (!jwtUtil.validateToken(token)) {
                log.warn("유효하지 않은 토큰");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("유효하지 않은 토큰입니다."));
            }

            // 2. JWT 토큰에서 Client ID 추출 (로깅용)
            String clientId = jwtUtil.extractClientId(token);
            String scope = jwtUtil.extractScope(token);
            log.info("토큰 인증 성공 - clientId: {}, scope: {}", clientId, scope);

            // 3. IRP 실물이전 처리
            IrpTransferResponse response = accountService.irpTransfer(request);

            log.info("IRP 실물이전 성공 - 입금금액: {}", response.getDepositAmt());

            return ResponseEntity.ok(
                    ApiResponse.success("IRP 실물이전이 성공적으로 완료되었습니다.", response)
            );

        } catch (IllegalArgumentException e) {
            log.warn("IRP 실물이전 실패 - 잘못된 요청: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("외부 API 호출 오류: " + e.getResponseBodyAsString(), e);
            return ResponseEntity.status(e.getStatusCode())
                    .body(ApiResponse.error("외부 기관 통신 오류: " + e.getResponseBodyAsString()));

        } catch (Exception e) {
            log.error("IRP 실물이전 실패 - 시스템 오류: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("IRP 실물이전 중 시스템 오류가 발생했습니다."));
        }
    }

    /**
     * 계좌주명 조회 API (v2.0)
     */
    @PostMapping("/account/holder-name")
    @Operation(
            summary = "계좌주명 조회",
            description = "계좌번호와 은행코드만으로 간단하게 계좌주명을 조회합니다. " +
                    "기존 계좌 상세정보 조회보다 단순화된 API로, 예금주명만 반환합니다.",
            tags = {"계좌 관리"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "계좌주명 조회 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 (필수 파라미터 누락, 잘못된 형식 등)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 (유효하지 않은 토큰)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "계좌를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<AccountHolderNameResponse>> getAccountHolderName(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authorization,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "계좌주명 조회 요청 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = AccountHolderNameRequest.class))
            )
            @RequestBody AccountHolderNameRequest request) {

        try {
            log.info("계좌주명 조회 요청 수신 - 은행코드: {}, 계좌번호: {}", request.getBankCode(), request.getAccountNumber());

            // 1. Authorization 헤더 토큰 추출 및 검증
            String token = jwtUtil.extractTokenFromHeader(authorization);
            if (!jwtUtil.validateToken(token)) {
                log.warn("유효하지 않은 토큰");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("유효하지 않은 토큰입니다."));
            }

            // 2. JWT 토큰에서 Client ID 추출 (로깅용)
            String clientId = jwtUtil.extractClientId(token);
            String scope = jwtUtil.extractScope(token);
            log.info("토큰 인증 성공 - clientId: {}, scope: {}", clientId, scope);

            // 3. 계좌주명 조회 처리
            AccountHolderNameResponse response = accountService.getAccountHolderName(request);

            log.info("계좌주명 조회 성공 - 계좌주명: {}", response.getAccountHolderName());

            return ResponseEntity.ok(
                    ApiResponse.success("계좌주명 조회가 성공적으로 완료되었습니다.", response)
            );

        } catch (IllegalArgumentException e) {
            log.warn("계좌주명 조회 실패 - 잘못된 요청: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("외부 API 호출 오류: " + e.getResponseBodyAsString(), e);
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("존재하지 않는 계좌입니다."));
            }
            return ResponseEntity.status(e.getStatusCode())
                    .body(ApiResponse.error("외부 기관 통신 오류: " + e.getResponseBodyAsString()));

        } catch (Exception e) {
            log.error("계좌주명 조회 실패 - 시스템 오류: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("계좌주명 조회 중 시스템 오류가 발생했습니다."));
        }
    }
} 