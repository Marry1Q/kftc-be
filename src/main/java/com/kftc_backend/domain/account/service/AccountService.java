package com.kftc_backend.domain.account.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kftc_backend.domain.account.dto.request.AccountDetailsRequest;
import com.kftc_backend.domain.account.dto.request.AccountInfoRequest;
import com.kftc_backend.domain.account.dto.request.BalanceRequest;
import com.kftc_backend.domain.account.dto.request.TransactionListRequest;
import com.kftc_backend.domain.account.dto.request.WithdrawRequest;
import com.kftc_backend.domain.account.dto.request.DepositRequest;
import com.kftc_backend.domain.account.dto.request.ExternalAccountDetailRequest;
import com.kftc_backend.domain.account.dto.request.ExternalAccountRequest;
import com.kftc_backend.domain.account.dto.request.ExternalBalanceRequest;
import com.kftc_backend.domain.account.dto.request.ExternalTransactionRequest;
import com.kftc_backend.domain.account.dto.request.ExternalWithdrawRequest;
import com.kftc_backend.domain.account.dto.request.ExternalDepositRequest;
import com.kftc_backend.domain.account.dto.request.IrpTransferRequest;
import com.kftc_backend.domain.account.dto.request.ExternalIrpWithdrawRequest;
import com.kftc_backend.domain.account.dto.request.ExternalIrpDepositRequest;
import com.kftc_backend.domain.account.dto.request.AccountHolderNameRequest;
import com.kftc_backend.domain.account.dto.response.AccountDetailInfoResponse;
import com.kftc_backend.domain.account.dto.response.AccountInfoResponse;
import com.kftc_backend.domain.account.dto.response.BalanceResponse;
import com.kftc_backend.domain.account.dto.response.TransactionListResponse;
import com.kftc_backend.domain.account.dto.response.WithdrawResponse;
import com.kftc_backend.domain.account.dto.response.DepositResponse;
import com.kftc_backend.domain.account.dto.response.ExternalAccountDetailResponse;
import com.kftc_backend.domain.account.dto.response.ExternalAccountResponse;
import com.kftc_backend.domain.account.dto.response.ExternalBalanceResponse;
import com.kftc_backend.domain.account.dto.response.ExternalTransactionResponse;
import com.kftc_backend.domain.account.dto.response.ExternalWithdrawResponse;
import com.kftc_backend.domain.account.dto.response.ExternalDepositResponse;
import com.kftc_backend.domain.account.dto.response.IrpTransferResponse;
import com.kftc_backend.domain.account.dto.response.ExternalIrpWithdrawResponse;
import com.kftc_backend.domain.account.dto.response.ExternalIrpDepositResponse;
import com.kftc_backend.domain.account.dto.response.AccountHolderNameResponse;
import com.kftc_backend.domain.account.entity.Account;
import com.kftc_backend.domain.account.repository.AccountRepository;
import com.kftc_backend.domain.bank.entity.BankCode;
import com.kftc_backend.domain.bank.repository.BankCodeRepository;
import com.kftc_backend.domain.user.entity.User;
import com.kftc_backend.domain.user.repository.UserRepository;
import com.kftc_backend.global.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AccountService {
    
    private final AccountRepository accountRepository;
    private final BankCodeRepository bankCodeRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public BalanceResponse getAccountBalance(BalanceRequest request) {
        // 1. userSeqNo로 User를 찾아 userCi 조회
        User user = userRepository.findById(request.getUserSeqNo())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. userSeqNo: " + request.getUserSeqNo()));
        String userCi = user.getUserCi();

        // 2. bankCodeStd로 BankCode를 찾아 endpoint 조회
        BankCode bankCode = bankCodeRepository.findById(request.getBankCodeStd())
                .orElseThrow(() -> new IllegalArgumentException("은행 코드를 찾을 수 없습니다. bankCodeStd: " + request.getBankCodeStd()));
        String externalApiUrl = bankCode.getBankEndpoint() + "/api/v1/accounts/balance";

        // 3. 외부 기관 API 호출
        ExternalBalanceRequest externalRequest = new ExternalBalanceRequest(userCi, request.getAccountNum());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ExternalBalanceRequest> entity = new HttpEntity<>(externalRequest, headers);

        ExternalBalanceResponse externalResponse = restTemplate.postForObject(externalApiUrl, entity, ExternalBalanceResponse.class);

        if (externalResponse == null || !externalResponse.isSuccess() || externalResponse.getData() == null) {
            throw new RuntimeException("외부 계좌 잔액 조회 API 호출에 실패했습니다.");
        }

        // 4. 외부 응답을 우리 API 응답 형식으로 변환
        ExternalBalanceResponse.ExternalBalanceData data = externalResponse.getData();
        return BalanceResponse.builder()
                .bankName(data.getBankName())
                .accountNum(data.getAccountNum())
                .balanceAmt(data.getBalanceAmt())
                .availableAmt(data.getAvailableAmt())
                .accountType(data.getAccountType())
                .productName(data.getProductName())
                .accountIssueDate(data.getAccountIssueDate())
                .maturityDate(data.getMaturityDate())
                .lastTranDate(data.getLastTranDate())
                .build();
    }
    
    @Transactional
    public AccountInfoResponse getAccountList(AccountInfoRequest request) {
        
        log.info("계좌통합조회 요청 수신 - userName: {}, inquiryBankType: {}, inquiryRecordCnt: {}",
                request.getUserName(), request.getInquiryBankType(), request.getInquiryRecordCnt());
        
        // 1. 요청 유효성 검사
        validateAccountInfoRequest(request);
        
        // 2. 외부 API들로부터 계좌 정보 조회
        List<AccountInfoResponse.AccountInfo> allAccounts = new ArrayList<>();
        List<BankCode> bankCodes = bankCodeRepository.findAll();

        for (BankCode bank : bankCodes) {
            if (bank.getBankEndpoint() == null || bank.getBankEndpoint().isEmpty()) {
                log.warn("Bank endpoint is not configured for bank_code_std: {}", bank.getBankCodeStd());
                continue;
            }
            String apiUrl = bank.getBankEndpoint() + "/api/v1/accounts/search-by-user-num";
            try {
                List<AccountInfoResponse.AccountInfo> accounts = fetchAccountsFromExternalApi(apiUrl, request.getUserNum(), request.getUserName());
                allAccounts.addAll(accounts);
                log.info("외부 API 조회 성공 - URL: {}, 조회 건수: {}", apiUrl, accounts.size());
            } catch (Exception e) {
                log.warn("외부 API 조회 실패 - URL: {}, 오류: {}", apiUrl, e.getMessage());
                // 하나의 API가 실패해도 다른 API로 계속 진행
            }
        }
        
        // 3. 요청한 건수만큼 제한
        int requestedCount = Integer.parseInt(request.getInquiryRecordCnt());
        if (allAccounts.size() > requestedCount) {
            allAccounts = allAccounts.subList(0, requestedCount);
        }
        
        log.info("계좌통합조회 완료 - 총 조회 건수: {}", allAccounts.size());
        
        // 4. 응답 생성
        return AccountInfoResponse.builder()
                .resList(allAccounts)
                .build();
    }
    
    public AccountDetailInfoResponse getAccountDetailInfo(String token, AccountDetailsRequest request) {
        // 1. 토큰 검증
        jwtUtil.validateToken(token);
        log.info("Access Token validation successful.");

        // 2. userSeqNo로 User 조회하여 userCi 획득
        User user = userRepository.findById(request.getUserSeqNo())
                .orElseThrow(() -> new IllegalArgumentException("User not found for userSeqNo: " + request.getUserSeqNo()));
        log.info("User found for userSeqNo: {}", request.getUserSeqNo());

        // 3. bankCodeStd로 BankCode 조회하여 bankEndpoint 획득
        BankCode bankCode = bankCodeRepository.findById(request.getBankCodeStd())
                .orElseThrow(() -> new IllegalArgumentException("BankCode not found for bankCodeStd: " + request.getBankCodeStd()));
        log.info("BankCode found for bankCodeStd: {}", request.getBankCodeStd());

        String bankEndpoint = bankCode.getBankEndpoint();
        if (bankEndpoint == null || bankEndpoint.trim().isEmpty()) {
            throw new IllegalStateException("Bank endpoint is not configured for bank: " + request.getBankCodeStd());
        }

        // 4. 외부 기관 API 호출
        String externalApiUrl = bankEndpoint + "/api/v1/accounts/detail";
        ExternalAccountDetailRequest externalRequest = ExternalAccountDetailRequest.builder()
                .userCi(user.getUserCi())
                .accountNum(request.getAccountNum())
                .build();

        log.info("Requesting account details from external API: {}", externalApiUrl);
        ResponseEntity<ExternalAccountDetailResponse> responseEntity = restTemplate.exchange(
                externalApiUrl,
                HttpMethod.POST,
                new HttpEntity<>(externalRequest, createJsonHeaders()),
                ExternalAccountDetailResponse.class
        );

        ExternalAccountDetailResponse externalResponse = responseEntity.getBody();
        if (externalResponse == null || !externalResponse.isSuccess() || externalResponse.getData() == null) {
            log.error("Failed to fetch account details from {}. Response: {}", externalApiUrl, externalResponse);
            throw new RuntimeException("외부 기관으로부터 계좌 상세 정보를 가져오는데 실패했습니다.");
        }
        log.info("Successfully received account details from external API.");

        // 5. DB에서 Account 정보 조회하여 fintechUseNum 획득
        Account account = accountRepository.findByAccountNumAndBankCode_BankCodeStd(request.getAccountNum(), request.getBankCodeStd())
                .orElseThrow(() -> new IllegalArgumentException("Account not found in our DB for accountNum: " + request.getAccountNum()));

        // 6. 최종 응답으로 매핑
        ExternalAccountDetailResponse.DetailData data = externalResponse.getData();
        return AccountDetailInfoResponse.builder()
                .bankName(data.getBankName())
                .savingsBankName(data.getSavingsBankName())
                .userSeqNo(request.getUserSeqNo())
                .accountNum(data.getAccountNum())
                .accountSeq(data.getAccountSeq())
                .accountType(mapAccountType(data.getAccountType()))
                .scope(data.getScope())
                .fintechUseNum(account.getFintechUseNum()) // DB에서 조회한 값 사용
                .build();
    }

    private HttpHeaders createJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
    
    private String mapAccountType(String externalAccountType) {
        if ("수시입출금".equals(externalAccountType)) {
            return "1";
        }
        return externalAccountType;
    }
    
    /**
     * 외부 API로부터 계좌 정보 조회
     */
    private List<AccountInfoResponse.AccountInfo> fetchAccountsFromExternalApi(String apiUrl, String userNum, String userName) {
        try {
            // 요청 데이터 생성
            ExternalAccountRequest externalRequest = ExternalAccountRequest.builder()
                    .userNum(userNum)
                    .build();
            
            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // HTTP 요청 엔티티 생성
            HttpEntity<ExternalAccountRequest> requestEntity = new HttpEntity<>(externalRequest, headers);
            
            // 외부 API 호출
            ResponseEntity<ExternalAccountResponse> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    requestEntity,
                    ExternalAccountResponse.class
            );
            
            // 응답 처리
            ExternalAccountResponse externalResponse = response.getBody();
            if (externalResponse != null && externalResponse.isSuccess() && externalResponse.getData() != null) {
                return convertToAccountInfoList(externalResponse.getData().getAccounts(), userName);
            } else {
                log.warn("외부 API 응답이 성공하지 않음 - URL: {}", apiUrl);
                return new ArrayList<>();
            }
            
        } catch (Exception e) {
            log.error("외부 API 호출 오류 - URL: {}, 오류: {}", apiUrl, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 외부 API 응답을 내부 응답 형식으로 변환
     */
    private List<AccountInfoResponse.AccountInfo> convertToAccountInfoList(List<ExternalAccountResponse.Account> externalAccounts, String userName) {
        List<AccountInfoResponse.AccountInfo> accountInfoList = new ArrayList<>();
        
        int listCounter = 1;
        for (ExternalAccountResponse.Account external : externalAccounts) {
            AccountInfoResponse.AccountInfo accountInfo = AccountInfoResponse.AccountInfo.builder()
                    .listNum(String.valueOf(listCounter++))
                    .bankCodeStd(external.getBankCodeStd())
                    .activityType(external.getActivityType())
                    .accountType(external.getAccountType())
                    .accountNum(external.getAccountNum())
                    .accountNumMasked(external.getAccountNumMasked())
                    .accountSeq(external.getAccountSeq())
                    .accountHolderName(userName) // 요청의 userName 사용
                    .accountIssueDate(external.getAccountIssueDate())
                    .lastTranDate(external.getLastTranDate())
                    .productName(external.getProductName())
                    .productSubName(external.getProductSubName())
                    .dormancyYn(external.getDormancyYn())
                    .balanceAmt(external.getBalanceAmt() != null ? String.valueOf(external.getBalanceAmt()) : "0")
                    .depositAmt(external.getDepositAmt() != null ? String.valueOf(external.getDepositAmt()) : "0")
                    .balanceCalcBasis1(external.getBalanceCalcBasis1())
                    .balanceCalcBasis2(external.getBalanceCalcBasis2())
                    .investmentLinkedYn(external.getInvestmentLinkedYn())
                    .bankLinkedYn(external.getBankLinkedYn())
                    .balanceAfterCancelYn(external.getBalanceAfterCancelYn())
                    .savingsBankCode(external.getSavingsBankCode() != null ? external.getSavingsBankCode() : "")
                    .build();
            
            accountInfoList.add(accountInfo);
        }
        
        return accountInfoList;
    }
    
    private void validateAccountInfoRequest(AccountInfoRequest request) {
        
        // 필수 필드 검증
        if (request.getUserName() == null || request.getUserName().trim().isEmpty()) {
            throw new IllegalArgumentException("사용자명(userName)은 필수입니다.");
        }
        
        if (request.getUserNum() == null || request.getUserNum().trim().isEmpty()) {
            throw new IllegalArgumentException("사용자 주민등록번호(userNum)는 필수입니다.");
        }
        
        if (request.getUserEmail() == null || request.getUserEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("사용자 이메일(userEmail)은 필수입니다.");
        }
        
        if (request.getAinfoAgreeYn() == null || request.getAinfoAgreeYn().trim().isEmpty()) {
            throw new IllegalArgumentException("어카운트인포 서비스 동의여부(ainfoAgreeYn)는 필수입니다.");
        }
        
        if (request.getInquiryBankType() == null || request.getInquiryBankType().trim().isEmpty()) {
            throw new IllegalArgumentException("금융기관 업권 구분(inquiryBankType)은 필수입니다.");
        }
        
        if (request.getTraceNo() == null || request.getTraceNo().trim().isEmpty()) {
            throw new IllegalArgumentException("추적번호(traceNo)는 필수입니다.");
        }
        
        if (request.getInquiryRecordCnt() == null || request.getInquiryRecordCnt().trim().isEmpty()) {
            throw new IllegalArgumentException("조회 건수(inquiryRecordCnt)는 필수입니다.");
        }
        
        // 형식 검증
        if (request.getUserName().length() > 20) {
            throw new IllegalArgumentException("사용자명(userName)은 20자 이하여야 합니다.");
        }
        
        // 주민등록번호 형식 검증 (999999-9999999)
        if (request.getUserNum().length() != 14) {
            throw new IllegalArgumentException("사용자 주민등록번호(userNum)는 999999-9999999 형식(14자리)이어야 합니다.");
        }
        
        // 주민등록번호 패턴 검증
        if (!isValidUserNumFormat(request.getUserNum())) {
            throw new IllegalArgumentException("사용자 주민등록번호(userNum)는 999999-9999999 형식이어야 합니다.");
        }
        
        if (request.getUserEmail().length() > 100) {
            throw new IllegalArgumentException("사용자 이메일(userEmail)은 100자 이하여야 합니다.");
        }
        
        if (request.getAinfoAgreeYn().length() != 1 || !"Y".equalsIgnoreCase(request.getAinfoAgreeYn())) {
            throw new IllegalArgumentException("어카운트인포 서비스 동의여부(ainfoAgreeYn)는 'Y'여야 합니다.");
        }
        
        if (request.getInquiryBankType().length() != 1) {
            throw new IllegalArgumentException("금융기관 업권 구분(inquiryBankType)은 1자리여야 합니다.");
        }
        
        if (request.getTraceNo().length() > 20) {
            throw new IllegalArgumentException("추적번호(traceNo)는 20자 이하여야 합니다.");
        }
        
        try {
            int recordCnt = Integer.parseInt(request.getInquiryRecordCnt());
            if (recordCnt <= 0 || recordCnt > 100) {
                throw new IllegalArgumentException("조회 건수(inquiryRecordCnt)는 1에서 100 사이여야 합니다.");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("조회 건수(inquiryRecordCnt)는 숫자여야 합니다.");
        }
        
        if (!isValidEmail(request.getUserEmail())) {
            throw new IllegalArgumentException("유효하지 않은 이메일 형식입니다.");
        }
    }
    
    /**
     * 이메일 형식 유효성 검증 (정규식 사용)
     */
    private boolean isValidEmail(String email) {
        String emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$";
        return email != null && email.matches(emailRegex);
    }
    
    /**
     * 주민등록번호 형식 유효성 검증
     */
    private boolean isValidUserNumFormat(String userNum) {
        // 999999-9999999 형식 검증
        String userNumRegex = "^\\d{6}-\\d{7}$";
        return userNum != null && userNum.matches(userNumRegex);
    }
    
    /**
     * 거래내역조회
     */
    public TransactionListResponse getTransactionList(TransactionListRequest request) {
        log.info("거래내역조회 요청 수신 - userSeqNo: {}, accountNum: {}, bankCodeStd: {}", 
                request.getUserSeqNo(), request.getAccountNum(), request.getBankCodeStd());
        
        // 1. userSeqNo로 User를 찾아 userCi 조회
        log.info("사용자 조회 시도 - userSeqNo: '{}', 타입: {}", request.getUserSeqNo(), request.getUserSeqNo().getClass().getSimpleName());
        
        // 디버깅: 전체 사용자 목록 확인
        long totalUsers = userRepository.count();
        log.info("전체 사용자 수: {}", totalUsers);
        
        // 디버깅: 해당 사용자 존재 여부 확인
        boolean exists = userRepository.existsById(request.getUserSeqNo());
        log.info("사용자 존재 여부 - userSeqNo: '{}', exists: {}", request.getUserSeqNo(), exists);
        
        User user = userRepository.findById(request.getUserSeqNo())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. userSeqNo: " + request.getUserSeqNo()));
        String userCi = user.getUserCi();
        log.info("사용자 조회 성공 - userSeqNo: {}, userCi: {}", request.getUserSeqNo(), userCi);

        // 2. bankCodeStd로 BankCode를 찾아 endpoint 조회
        BankCode bankCode = bankCodeRepository.findById(request.getBankCodeStd())
                .orElseThrow(() -> new IllegalArgumentException("은행 코드를 찾을 수 없습니다. bankCodeStd: " + request.getBankCodeStd()));
        String externalApiUrl = bankCode.getBankEndpoint() + "/api/v1/transactions/history";
        log.info("은행 엔드포인트 조회 성공 - bankCodeStd: {}, endpoint: {}", request.getBankCodeStd(), externalApiUrl);

        // 3. 외부 기관 API 호출을 위한 요청 생성
        ExternalTransactionRequest externalRequest = ExternalTransactionRequest.builder()
                .userCi(userCi)
                .accountNum(request.getAccountNum())
                .inquiryType(request.getInquiryType() != null ? request.getInquiryType() : "A") // 기본값 A
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : "D") // 기본값 D
                .build();

        // 4. 외부 기관 API 호출
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ExternalTransactionRequest> entity = new HttpEntity<>(externalRequest, headers);

        log.info("외부 API 호출 시작 - URL: {}", externalApiUrl);
        ExternalTransactionResponse externalResponse = restTemplate.postForObject(externalApiUrl, entity, ExternalTransactionResponse.class);

        if (externalResponse == null || !externalResponse.isSuccess() || externalResponse.getData() == null) {
            log.error("외부 거래내역조회 API 호출 실패 - URL: {}", externalApiUrl);
            throw new RuntimeException("외부 거래내역조회 API 호출에 실패했습니다.");
        }
        log.info("외부 API 호출 성공 - 거래 건수: {}", 
                externalResponse.getData().getResList() != null ? externalResponse.getData().getResList().size() : 0);

        // 5. 외부 응답을 우리 API 응답 형식으로 변환
        ExternalTransactionResponse.ExternalTransactionData data = externalResponse.getData();
        
        // 거래내역 변환
        List<TransactionListResponse.TransactionInfo> transactionList = new ArrayList<>();
        if (data.getResList() != null) {
            for (ExternalTransactionResponse.ExternalTransactionInfo externalTrans : data.getResList()) {
                TransactionListResponse.TransactionInfo transactionInfo = TransactionListResponse.TransactionInfo.builder()
                        .tranDate(externalTrans.getTranDate())
                        .tranTime(externalTrans.getTranTime())
                        .inoutType(externalTrans.getInoutType())
                        .tranType(externalTrans.getTranType())
                        .printedContent(externalTrans.getPrintedContent())
                        .tranAmt(externalTrans.getTranAmt())
                        .afterBalanceAmt(externalTrans.getAfterBalanceAmt())
                        .branchName(externalTrans.getBranchName())
                        .isSafeAccountDeposit(externalTrans.getIsSafeAccountDeposit())  // 안심계좌 입금 상태 추가
                        .build();
                transactionList.add(transactionInfo);
            }
        }

        TransactionListResponse response = TransactionListResponse.builder()
                .bankName(bankCode.getBankName()) // 데이터베이스에서 조회한 은행명 사용
                .accountNum(request.getAccountNum()) // 클라이언트 요청에서 받은 계좌번호 사용
                .balanceAmt(data.getBalanceAmt())
                .pageRecordCnt(data.getPageRecordCnt())
                .nextPageYn(data.getNextPageYn())
                .beforInquiryTraceInfo(data.getBeforInquiryTraceInfo())
                .resList(transactionList)
                .build();

        log.info("거래내역조회 성공 - 총 거래 건수: {}", transactionList.size());
        return response;
    }

    /**
     * 출금이체
     */
    public WithdrawResponse withdraw(WithdrawRequest request) {
        log.info("출금이체 요청 수신 - userSeqNo: {}, wdAccountNum: {}, tranAmt: {}", 
                request.getUserSeqNo(), request.getWdAccountNum(), request.getTranAmt());
        
        // ===== 클라이언트에서 받은 전체 요청 데이터 로그 =====
        log.info("=== 출금이체 클라이언트 요청 데이터 상세 로그 ===");
        log.info("bankTranId: {}", request.getBankTranId());
        log.info("cntrAccountType: {}", request.getCntrAccountType());
        log.info("cntrAccountNum: {}", request.getCntrAccountNum());
        log.info("dpsPrintContent: {}", request.getDpsPrintContent());
        log.info("wdBankCodeStd: {}", request.getWdBankCodeStd());
        log.info("wdAccountNum: {}", request.getWdAccountNum());
        log.info("tranAmt: {}", request.getTranAmt());
        log.info("userSeqNo: {}", request.getUserSeqNo());
        log.info("tranDtime: {}", request.getTranDtime());
        log.info("reqClientName: {}", request.getReqClientName());
        log.info("reqClientNum: {}", request.getReqClientNum());
        log.info("transferPurpose: {}", request.getTransferPurpose());
        log.info("recvClientName: {}", request.getRecvClientName());
        log.info("recvClientBankCode: {}", request.getRecvClientBankCode());
        log.info("recvClientAccountNum: {}", request.getRecvClientAccountNum());
        
        // JSON 문자열로 변환하여 로그 출력
        try {
            String jsonRequestBody = objectMapper.writeValueAsString(request);
            log.info("클라이언트 요청 JSON: {}", jsonRequestBody);
        } catch (Exception e) {
            log.warn("JSON 변환 실패: {}", e.getMessage());
        }
        log.info("=== 출금이체 클라이언트 요청 데이터 로그 끝 ===");
        
        // 1. userSeqNo로 User를 찾아 userCi 조회
        User user = userRepository.findById(request.getUserSeqNo())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. userSeqNo: " + request.getUserSeqNo()));
        String userCi = user.getUserCi();
        log.info("사용자 조회 성공 - userSeqNo: {}, userCi: {}", request.getUserSeqNo(), userCi);

        // 2. wdBankCodeStd로 BankCode를 찾아 endpoint 조회
        BankCode bankCode = bankCodeRepository.findById(request.getWdBankCodeStd())
                .orElseThrow(() -> new IllegalArgumentException("은행 코드를 찾을 수 없습니다. wdBankCodeStd: " + request.getWdBankCodeStd()));
        String externalApiUrl = bankCode.getBankEndpoint() + "/api/v1/transactions/withdraw";
        log.info("은행 엔드포인트 조회 성공 - wdBankCodeStd: {}, endpoint: {}", request.getWdBankCodeStd(), externalApiUrl);

        // 3. 외부 기관 API 호출을 위한 요청 생성
        ExternalWithdrawRequest externalRequest = ExternalWithdrawRequest.builder()
                .userCI(userCi)
                .bankTranId(request.getBankTranId())
                .dpsPrintContent(request.getDpsPrintContent())
                .wdBankCodeStd(request.getWdBankCodeStd())
                .wdAccountNum(request.getWdAccountNum())
                .tranAmt(Long.parseLong(request.getTranAmt())) // String을 Long으로 변환
                .tranDtime(request.getTranDtime())
                .reqClientName(request.getReqClientName())
                .build();

        // ===== 제3기관에 보내는 요청 데이터 로그 =====
        log.info("-----------------------------------------------------");
        log.info("🔗 [AUTH-BACKEND → HANA-BACKEND] 출금이체 API 요청");
        log.info("-----------------------------------------------------");
        log.info("🎯 URL: {}", externalApiUrl);
        log.info("📤 요청 데이터:");
        log.info("  ├─ userCI: {}", externalRequest.getUserCI());
        log.info("  ├─ 은행거래ID: {}", externalRequest.getBankTranId());
        log.info("  ├─ 출금계좌번호: {}", externalRequest.getWdAccountNum());
        log.info("  ├─ 거래금액: {}", externalRequest.getTranAmt());
        log.info("  ├─ 거래시간: {}", externalRequest.getTranDtime());
        log.info("  └─ 요청자명: {}", externalRequest.getReqClientName());
        log.info("⏰ 요청 시간: {}", java.time.LocalDateTime.now());
        log.info("-----------------------------------------------------");

        // 4. 외부 기관 API 호출
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ExternalWithdrawRequest> entity = new HttpEntity<>(externalRequest, headers);

        log.info("외부 API 호출 시작 - URL: {}", externalApiUrl);
        
        try {
            ExternalWithdrawResponse externalResponse = restTemplate.postForObject(externalApiUrl, entity, ExternalWithdrawResponse.class);

            if (externalResponse == null || !externalResponse.isSuccess() || externalResponse.getData() == null) {
                log.error("-----------------------------------------------------");
                log.error("❌ [HANA-BACKEND → AUTH-BACKEND] 출금이체 API 응답 실패");
                log.error("-----------------------------------------------------");
                log.error("🎯 URL: {}", externalApiUrl);
                log.error("📥 응답: {}", externalResponse != null ? externalResponse.toString() : "null");
                log.error("⏰ 응답 시간: {}", java.time.LocalDateTime.now());
                log.error("-----------------------------------------------------");
                throw new RuntimeException("외부 출금이체 API 호출에 실패했습니다.");
            }
            
            log.info("-----------------------------------------------------");
            log.info("✅ [HANA-BACKEND → AUTH-BACKEND] 출금이체 API 응답 성공");
            log.info("-----------------------------------------------------");
            log.info("📥 응답 데이터:");
            log.info("  ├─ 은행거래ID: {}", externalResponse.getData().getBankTranId());
            log.info("  ├─ 계좌번호: {}", externalResponse.getData().getAccountNum());
            log.info("  ├─ 거래금액: {}", externalResponse.getData().getTranAmt());
            log.info("  ├─ 은행명: {}", externalResponse.getData().getBankName());
            log.info("  └─ 예금주명: {}", externalResponse.getData().getAccountHolderName());
            log.info("⏰ 응답 시간: {}", java.time.LocalDateTime.now());
            log.info("-----------------------------------------------------");

            // 5. 외부 응답을 우리 API 응답 형식으로 변환
            ExternalWithdrawResponse.ExternalWithdrawData data = externalResponse.getData();
            
            WithdrawResponse response = WithdrawResponse.builder()
                    .bankTranId(data.getBankTranId())
                    .dpsPrintContent(data.getDpsPrintContent())
                    .accountNum(data.getAccountNum())
                    .accountAlias(data.getAccountAlias())
                    .bankCodeStd(data.getBankCodeStd())
                    .bankCodeSub(data.getBankCodeSub())
                    .bankName(data.getBankName())
                    .accountNumMasked(data.getAccountNumMasked())
                    .printContent(data.getPrintContent())
                    .accountHolderName(data.getAccountHolderName())
                    .tranAmt(data.getTranAmt())
                    .wdLimitRemainAmt(data.getWdLimitRemainAmt())
                    .build();

            log.info("출금이체 성공 - 거래금액: {}", response.getTranAmt());
            return response;
            
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("=== 출금이체 외부 API 호출 실패 상세 정보 ===");
            log.error("HTTP 상태 코드: {}", e.getStatusCode());
            log.error("응답 본문: {}", e.getResponseBodyAsString());
            log.error("요청 URL: {}", externalApiUrl);
            log.error("=== 출금이체 외부 API 호출 실패 정보 끝 ===");
            throw e; // 원래 예외를 다시 던져서 Controller에서 처리하도록 함
        }
    }

    /**
     * 입금이체
     */
    public DepositResponse deposit(DepositRequest request) {
        log.info("입금이체 요청 수신 - accountNum: {}, tranAmt: {}", 
                request.getAccountNum(), request.getTranAmt());
        
        // ===== 클라이언트에서 받은 전체 요청 데이터 로그 =====
        log.info("=== 클라이언트 요청 데이터 상세 로그 ===");
        log.info("nameCheckOption: {}", request.getNameCheckOption());
        log.info("tranDtime: {}", request.getTranDtime());
        log.info("tranNo: {}", request.getTranNo());
        log.info("bankTranId: {}", request.getBankTranId());
        log.info("bankCodeStd: {}", request.getBankCodeStd());
        log.info("accountNum: {}", request.getAccountNum());
        log.info("accountHolderName: {}", request.getAccountHolderName());
        log.info("printContent: {}", request.getPrintContent());
        log.info("tranAmt: {}", request.getTranAmt());
        log.info("reqClientNum: {}", request.getReqClientNum());
        log.info("reqClientAccountNum: {}", request.getReqClientAccountNum());
        log.info("transferPurpose: {}", request.getTransferPurpose());
        
        // JSON 문자열로 변환하여 로그 출력
        try {
            String jsonRequestBody = objectMapper.writeValueAsString(request);
            log.info("클라이언트 요청 JSON: {}", jsonRequestBody);
        } catch (Exception e) {
            log.warn("JSON 변환 실패: {}", e.getMessage());
        }
        log.info("=== 클라이언트 요청 데이터 로그 끝 ===");
        
        // 1. bankCodeStd로 BankCode를 찾아 endpoint 조회
        BankCode bankCode = bankCodeRepository.findById(request.getBankCodeStd())
                .orElseThrow(() -> new IllegalArgumentException("은행 코드를 찾을 수 없습니다. bankCodeStd: " + request.getBankCodeStd()));
        String externalApiUrl = bankCode.getBankEndpoint() + "/api/v1/transactions/deposit";
        log.info("은행 엔드포인트 조회 성공 - bankCodeStd: {}, endpoint: {}", request.getBankCodeStd(), externalApiUrl);

        // 2. 외부 기관 API 호출을 위한 요청 생성
        ExternalDepositRequest externalRequest = ExternalDepositRequest.builder()
                .tranDtime(request.getTranDtime())
                .tranNo(request.getTranNo())
                .bankTranId(request.getBankTranId())
                .bankCodeStd(request.getBankCodeStd())
                .accountNum(request.getAccountNum())
                .accountHolderName(request.getAccountHolderName())
                .printContent(request.getPrintContent())
                .tranAmt(Long.parseLong(request.getTranAmt())) // String을 Long으로 변환
                .reqClientNum(request.getReqClientNum()) // reqClientNum을 그대로 사용
                .transferPurpose(request.getTransferPurpose())
                .build();

        // ===== 제3기관에 보내는 요청 데이터 로그 =====
        log.info("-----------------------------------------------------");
        log.info("🔗 [AUTH-BACKEND → HANA-BACKEND] 입금이체 API 요청");
        log.info("-----------------------------------------------------");
        log.info("🎯 URL: {}", externalApiUrl);
        log.info("📤 요청 데이터:");
        log.info("  ├─ 은행거래ID: {}", externalRequest.getBankTranId());
        log.info("  ├─ 입금계좌번호: {}", externalRequest.getAccountNum());
        log.info("  ├─ 거래금액: {}", externalRequest.getTranAmt());
        log.info("  ├─ 거래시간: {}", externalRequest.getTranDtime());
        log.info("  ├─ 예금주명: {}", externalRequest.getAccountHolderName());
        log.info("  └─ 요청고객번호: {}", externalRequest.getReqClientNum());
        log.info("⏰ 요청 시간: {}", java.time.LocalDateTime.now());
        log.info("-----------------------------------------------------");

        // 3. 외부 기관 API 호출
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ExternalDepositRequest> entity = new HttpEntity<>(externalRequest, headers);

        log.info("외부 API 호출 시작 - URL: {}", externalApiUrl);
        
        try {
            ExternalDepositResponse externalResponse = restTemplate.postForObject(externalApiUrl, entity, ExternalDepositResponse.class);

            if (externalResponse == null || !externalResponse.isSuccess() || externalResponse.getData() == null) {
                log.error("-----------------------------------------------------");
                log.error("❌ [HANA-BACKEND → AUTH-BACKEND] 입금이체 API 응답 실패");
                log.error("-----------------------------------------------------");
                log.error("🎯 URL: {}", externalApiUrl);
                log.error("📥 응답: {}", externalResponse != null ? externalResponse.toString() : "null");
                log.error("⏰ 응답 시간: {}", java.time.LocalDateTime.now());
                log.error("-----------------------------------------------------");
                throw new RuntimeException("외부 입금이체 API 호출에 실패했습니다.");
            }
            
            log.info("-----------------------------------------------------");
            log.info("✅ [HANA-BACKEND → AUTH-BACKEND] 입금이체 API 응답 성공");
            log.info("-----------------------------------------------------");
            log.info("📥 응답 데이터:");
            log.info("  ├─ 은행거래ID: {}", externalResponse.getData().getBankTranId());
            log.info("  ├─ 계좌번호: {}", externalResponse.getData().getAccountNum());
            log.info("  ├─ 거래금액: {}", externalResponse.getData().getTranAmt());
            log.info("  ├─ 은행명: {}", externalResponse.getData().getBankName());
            log.info("  ├─ 예금주명: {}", externalResponse.getData().getAccountHolderName());
            log.info("  └─ 출금은행거래ID: {}", externalResponse.getData().getWithdrawBankTranId());
            log.info("⏰ 응답 시간: {}", java.time.LocalDateTime.now());
            log.info("-----------------------------------------------------");

            // 4. 외부 응답을 우리 API 응답 형식으로 변환
            ExternalDepositResponse.ExternalDepositData data = externalResponse.getData();
            
            DepositResponse response = DepositResponse.builder()
                    .tranNo(data.getTranNo())
                    .bankTranId(data.getBankTranId())
                    .bankTranDate(data.getBankTranDate())
                    .bankCodeTran(data.getBankCodeTran())
                    .bankRspCode(data.getBankRspCode())
                    .bankRspMessage(data.getBankRspMessage())
                    .bankName(data.getBankName())
                    .accountNum(data.getAccountNum())
                    .accountNumMasked(data.getAccountNumMasked())
                    .printContent(data.getPrintContent())
                    .accountHolderName(data.getAccountHolderName())
                    .tranAmt(data.getTranAmt())
                    .withdrawBankTranId(data.getWithdrawBankTranId())
                    .build();

            log.info("입금이체 성공 - 거래금액: {}", response.getTranAmt());
            return response;
            
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("=== 외부 API 호출 실패 상세 정보 ===");
            log.error("HTTP 상태 코드: {}", e.getStatusCode());
            log.error("응답 본문: {}", e.getResponseBodyAsString());
            log.error("요청 URL: {}", externalApiUrl);
            log.error("=== 외부 API 호출 실패 정보 끝 ===");
            throw e; // 원래 예외를 다시 던져서 Controller에서 처리하도록 함
        }
    }

    /**
     * IRP 실물이전
     */
    public IrpTransferResponse irpTransfer(IrpTransferRequest request) {
        log.info("IRP 실물이전 요청 수신 - userSeqNo: {}, wdAccountNum: {}, rsvAccountNum: {}", 
                request.getUserSeqNo(), request.getWdAccountNum(), request.getRsvAccountNum());
        
        // ===== 클라이언트에서 받은 전체 요청 데이터 로그 =====
        log.info("=== IRP 실물이전 클라이언트 요청 데이터 상세 로그 ===");
        log.info("userSeqNo: {}", request.getUserSeqNo());
        log.info("bankTranId: {}", request.getBankTranId());
        log.info("wdAccountNum: {}", request.getWdAccountNum());
        log.info("rsvAccountNum: {}", request.getRsvAccountNum());
        log.info("wdBankCodeStd: {}", request.getWdBankCodeStd());
        log.info("rsvBankCodeStd: {}", request.getRsvBankCodeStd());
        log.info("tranDtime: {}", request.getTranDtime());
        log.info("reqClientName: {}", request.getReqClientName());
        
        // JSON 문자열로 변환하여 로그 출력
        try {
            String jsonRequestBody = objectMapper.writeValueAsString(request);
            log.info("클라이언트 요청 JSON: {}", jsonRequestBody);
        } catch (Exception e) {
            log.warn("JSON 변환 실패: {}", e.getMessage());
        }
        log.info("=== IRP 실물이전 클라이언트 요청 데이터 로그 끝 ===");
        
        // 1. userSeqNo로 User를 찾아 userCi 조회
        log.info("=== 사용자 조회 시작 ===");
        log.info("조회할 userSeqNo: '{}', 타입: {}", request.getUserSeqNo(), request.getUserSeqNo().getClass().getSimpleName());
        
        // 전체 사용자 수 확인
        long totalUsers = userRepository.count();
        log.info("전체 사용자 수: {}", totalUsers);
        
        // 해당 사용자 존재 여부 확인
        boolean exists = userRepository.existsById(request.getUserSeqNo());
        log.info("사용자 존재 여부 - userSeqNo: '{}', exists: {}", request.getUserSeqNo(), exists);
        
        User user = userRepository.findById(request.getUserSeqNo())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. userSeqNo: " + request.getUserSeqNo()));
        String userCi = user.getUserCi();
        log.info("사용자 조회 성공 - userSeqNo: {}, userCi: {}", request.getUserSeqNo(), userCi);
        
        // userCi 값 검증
        log.info("=== userCi 값 검증 시작 ===");
        if (userCi == null) {
            log.error("CRITICAL: userCi가 null입니다 - userSeqNo: {}", request.getUserSeqNo());
            throw new IllegalStateException("사용자 CI 정보가 null입니다.");
        }
        if (userCi.trim().isEmpty()) {
            log.error("CRITICAL: userCi가 비어있습니다 - userSeqNo: {}", request.getUserSeqNo());
            throw new IllegalStateException("사용자 CI 정보가 비어있습니다.");
        }
        log.info("userCi 검증 완료 - 길이: {}, 앞 10자: {}, 뒤 10자: {}", 
                userCi.length(), 
                userCi.substring(0, Math.min(10, userCi.length())),
                userCi.length() > 10 ? userCi.substring(userCi.length() - 10) : "");
        log.info("=== userCi 값 검증 완료 ===");

        // 2. wdBankCodeStd로 BankCode를 찾아 출금 endpoint 조회
        BankCode wdBankCode = bankCodeRepository.findById(request.getWdBankCodeStd())
                .orElseThrow(() -> new IllegalArgumentException("출금은행 코드를 찾을 수 없습니다. wdBankCodeStd: " + request.getWdBankCodeStd()));
        String withdrawApiUrl = wdBankCode.getBankEndpoint() + "/api/v1/retirement/withdraw";
        log.info("출금은행 엔드포인트 조회 성공 - wdBankCodeStd: {}, endpoint: {}", request.getWdBankCodeStd(), withdrawApiUrl);

        // 3. 외부 기관 IRP 출금 API 호출을 위한 요청 생성
        log.info("=== 외부 API 요청 객체 생성 시작 ===");
        log.info("Builder에 설정할 userCi: {}", userCi);
        
        ExternalIrpWithdrawRequest withdrawRequest = ExternalIrpWithdrawRequest.builder()
                .userCi(userCi)
                .bankTranId(request.getBankTranId())
                .wdBankCodeStd(request.getWdBankCodeStd())
                .wdAccountNum(request.getWdAccountNum())
                .rsvAccountNum(request.getRsvAccountNum())
                .tranDtime(request.getTranDtime())
                .reqClientName(request.getReqClientName())
                .build();

        // ===== 제3기관 출금 요청 데이터 로그 =====
        log.info("=== IRP 출금 제3기관 요청 데이터 상세 로그 ===");
        log.info("외부 API URL: {}", withdrawApiUrl);
        log.info("userCI: {}", withdrawRequest.getUserCi());
        log.info("CRITICAL CHECK - userCI 값이 null인가? {}", withdrawRequest.getUserCi() == null);
        log.info("CRITICAL CHECK - userCI 값이 비어있는가? {}", withdrawRequest.getUserCi() != null && withdrawRequest.getUserCi().trim().isEmpty());
        log.info("bankTranId: {}", withdrawRequest.getBankTranId());
        log.info("wdBankCodeStd: {}", withdrawRequest.getWdBankCodeStd());
        log.info("wdAccountNum: {}", withdrawRequest.getWdAccountNum());
        log.info("rsvAccountNum: {}", withdrawRequest.getRsvAccountNum());
        log.info("tranDtime: {}", withdrawRequest.getTranDtime());
        log.info("reqClientName: {}", withdrawRequest.getReqClientName());
        
        // JSON 문자열로 변환하여 로그 출력
        try {
            String jsonRequestBody = objectMapper.writeValueAsString(withdrawRequest);
            log.info("제3기관 출금 요청 JSON: {}", jsonRequestBody);
        } catch (Exception e) {
            log.warn("JSON 변환 실패: {}", e.getMessage());
        }
        log.info("=== IRP 출금 제3기관 요청 데이터 로그 끝 ===");

        // 4. 외부 기관 IRP 출금 API 호출
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ExternalIrpWithdrawRequest> withdrawEntity = new HttpEntity<>(withdrawRequest, headers);

        log.info("외부 IRP 출금 API 호출 시작 - URL: {}", withdrawApiUrl);
        
        ExternalIrpWithdrawResponse withdrawResponse;
        try {
            withdrawResponse = restTemplate.postForObject(withdrawApiUrl, withdrawEntity, ExternalIrpWithdrawResponse.class);

            if (withdrawResponse == null || !withdrawResponse.isSuccess() || withdrawResponse.getData() == null) {
                log.error("외부 IRP 출금 API 호출 실패 - URL: {}", withdrawApiUrl);
                log.error("외부 API 응답: {}", withdrawResponse != null ? withdrawResponse.toString() : "null");
                throw new RuntimeException("외부 IRP 출금 API 호출에 실패했습니다.");
            }
            log.info("외부 IRP 출금 API 호출 성공 - 출금금액: {}", withdrawResponse.getData().getWithdrawAmt());
            
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("=== IRP 출금 외부 API 호출 실패 상세 정보 ===");
            log.error("HTTP 상태 코드: {}", e.getStatusCode());
            log.error("응답 본문: {}", e.getResponseBodyAsString());
            log.error("요청 URL: {}", withdrawApiUrl);
            log.error("=== IRP 출금 외부 API 호출 실패 정보 끝 ===");
            throw e;
        }

        // 5. rsvBankCodeStd로 BankCode를 찾아 입금 endpoint 조회
        BankCode rsvBankCode = bankCodeRepository.findById(request.getRsvBankCodeStd())
                .orElseThrow(() -> new IllegalArgumentException("입금은행 코드를 찾을 수 없습니다. rsvBankCodeStd: " + request.getRsvBankCodeStd()));
        String depositApiUrl = rsvBankCode.getBankEndpoint() + "/api/v1/account/retirement/deposit";
        log.info("입금은행 엔드포인트 조회 성공 - rsvBankCodeStd: {}, endpoint: {}", request.getRsvBankCodeStd(), depositApiUrl);

        // 6. 외부 기관 IRP 입금 API 호출을 위한 요청 생성
        ExternalIrpDepositRequest depositRequest = ExternalIrpDepositRequest.builder()
                .userCI(userCi)
                .tranDtime(request.getTranDtime())
                .bankTranId(request.getBankTranId())
                .rsvBankCodeStd(request.getRsvBankCodeStd())
                .wdAccountNum(request.getWdAccountNum())
                .rsvAccountNum(request.getRsvAccountNum())
                .depositAmt(withdrawResponse.getData().getWithdrawAmt()) // 출금된 금액을 입금 금액으로 사용
                .reqClientName(request.getReqClientName())
                .build();

        // ===== 제3기관 입금 요청 데이터 로그 =====
        log.info("=== IRP 입금 제3기관 요청 데이터 상세 로그 ===");
        log.info("외부 API URL: {}", depositApiUrl);
        log.info("userCI: {}", depositRequest.getUserCI());
        log.info("tranDtime: {}", depositRequest.getTranDtime());
        log.info("bankTranId: {}", depositRequest.getBankTranId());
        log.info("rsvBankCodeStd: {}", depositRequest.getRsvBankCodeStd());
        log.info("wdAccountNum: {}", depositRequest.getWdAccountNum());
        log.info("rsvAccountNum: {}", depositRequest.getRsvAccountNum());
        log.info("depositAmt: {}", depositRequest.getDepositAmt());
        log.info("reqClientName: {}", depositRequest.getReqClientName());
        
        // JSON 문자열로 변환하여 로그 출력
        try {
            String jsonRequestBody = objectMapper.writeValueAsString(depositRequest);
            log.info("제3기관 입금 요청 JSON: {}", jsonRequestBody);
        } catch (Exception e) {
            log.warn("JSON 변환 실패: {}", e.getMessage());
        }
        log.info("=== IRP 입금 제3기관 요청 데이터 로그 끝 ===");

        // 7. 외부 기관 IRP 입금 API 호출
        HttpEntity<ExternalIrpDepositRequest> depositEntity = new HttpEntity<>(depositRequest, headers);

        log.info("외부 IRP 입금 API 호출 시작 - URL: {}", depositApiUrl);
        
        ExternalIrpDepositResponse depositResponse;
        try {
            depositResponse = restTemplate.postForObject(depositApiUrl, depositEntity, ExternalIrpDepositResponse.class);

            if (depositResponse == null || !depositResponse.isSuccess() || depositResponse.getData() == null) {
                log.error("외부 IRP 입금 API 호출 실패 - URL: {}", depositApiUrl);
                log.error("외부 API 응답: {}", depositResponse != null ? depositResponse.toString() : "null");
                throw new RuntimeException("외부 IRP 입금 API 호출에 실패했습니다.");
            }
            log.info("외부 IRP 입금 API 호출 성공 - 입금금액: {}", depositResponse.getData().getDepositAmt());
            
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("=== IRP 입금 외부 API 호출 실패 상세 정보 ===");
            log.error("HTTP 상태 코드: {}", e.getStatusCode());
            log.error("응답 본문: {}", e.getResponseBodyAsString());
            log.error("요청 URL: {}", depositApiUrl);
            log.error("=== IRP 입금 외부 API 호출 실패 정보 끝 ===");
            throw e;
        }

        // 8. 외부 응답을 우리 API 응답 형식으로 변환
        ExternalIrpDepositResponse.ExternalIrpDepositData data = depositResponse.getData();
        
        IrpTransferResponse response = IrpTransferResponse.builder()
                .wdAccountNum(data.getWdAccountNum())
                .rsvAccountNum(data.getRsvAccountNum())
                .maskedAccountNum(data.getMaskedAccountNum())
                .rsvBankCodeStd(data.getRsvBankCodeStd())
                .accountType(data.getAccountType())
                .irpType(data.getIrpType())
                .irpProductName(data.getIrpProductName())
                .maturityDate(data.getMaturityDate())
                .depositAmt(data.getDepositAmt())
                .balanceAmt(data.getBalanceAmt())
                .paymentPrd(data.getPaymentPrd())
                .updatedAt(data.getUpdatedAt())
                .build();

        log.info("IRP 실물이전 성공 - 입금금액: {}", response.getDepositAmt());
        return response;
    }

    /**
     * 계좌주명 조회
     * 
     * 계좌번호와 은행코드만으로 간단하게 계좌주명을 조회합니다.
     * hanaBank-backend의 새로운 /api/v1/accounts/holder-name API를 호출합니다.
     * 
     * @param request 계좌주명 조회 요청 (은행코드, 계좌번호)
     * @return 계좌주명 응답
     */
    @Transactional(readOnly = true)
    public AccountHolderNameResponse getAccountHolderName(AccountHolderNameRequest request) {
        log.info("계좌주명 조회 시작 - 은행코드: {}, 계좌번호: {}", request.getBankCode(), request.getAccountNumber());

        try {
            // 1. bankCode로 BankCode를 찾아 endpoint 조회
            BankCode bankCode = bankCodeRepository.findById(request.getBankCode())
                    .orElseThrow(() -> new IllegalArgumentException("은행 코드를 찾을 수 없습니다. bankCode: " + request.getBankCode()));
            
            String externalApiUrl = bankCode.getBankEndpoint() + "/api/v1/accounts/holder-name";
            log.info("은행 엔드포인트 조회 성공 - bankCode: {}, endpoint: {}", request.getBankCode(), externalApiUrl);

            // 2. 외부 기관 API 호출을 위한 요청 생성 (hanaBank-backend의 AccountHolderNameRequest 형식)
            // bankCode와 accountNumber를 그대로 전달
            AccountHolderNameRequest externalRequest = AccountHolderNameRequest.builder()
                    .bankCode(request.getBankCode())
                    .accountNumber(request.getAccountNumber())
                    .build();

            log.debug("외부 API 요청 데이터: {}", externalRequest);

            // 3. 외부 기관 API 호출
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<AccountHolderNameRequest> entity = new HttpEntity<>(externalRequest, headers);

            log.info("외부 API 호출 시작 - URL: {}", externalApiUrl);
            ResponseEntity<String> response = restTemplate.exchange(
                    externalApiUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            String responseBody = response.getBody();
            log.debug("외부 API 응답: {}", responseBody);

            if (responseBody == null || responseBody.trim().isEmpty()) {
                log.warn("외부 API 응답이 비어있습니다.");
                throw new IllegalArgumentException("계좌 정보를 조회할 수 없습니다.");
            }

            // 4. JSON 응답 파싱 (hanaBank-backend의 ApiResponse<AccountHolderNameResponse> 형식)
            try {
                com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(responseBody);
                
                // success 필드 확인
                boolean success = jsonNode.path("success").asBoolean(false);
                if (!success) {
                    String errorMessage = jsonNode.path("message").asText("알 수 없는 오류");
                    log.warn("외부 API 오류 응답: {}", errorMessage);
                    throw new IllegalArgumentException(errorMessage);
                }
                
                // data.accountHolderName 추출
                String accountHolderName = jsonNode.path("data").path("accountHolderName").asText(null);
                if (accountHolderName == null || accountHolderName.trim().isEmpty()) {
                    log.warn("계좌주명이 비어있습니다 - 응답: {}", responseBody);
                    throw new IllegalArgumentException("계좌주명을 확인할 수 없습니다.");
                }

                log.info("계좌주명 조회 성공 - 계좌주명: {}", accountHolderName);

                return AccountHolderNameResponse.builder()
                        .accountHolderName(accountHolderName.trim())
                        .build();
                        
            } catch (Exception e) {
                log.error("JSON 파싱 오류 - 응답: {}, 오류: {}", responseBody, e.getMessage());
                throw new RuntimeException("응답 데이터 파싱 중 오류가 발생했습니다.", e);
            }

        } catch (IllegalArgumentException e) {
            log.error("잘못된 요청 - 은행코드: {}, 계좌번호: {}, 오류: {}", 
                     request.getBankCode(), request.getAccountNumber(), e.getMessage());
            throw e;

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("외부 API 호출 실패 - HTTP 상태: {}, 응답: {}", 
                     e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 404) {
                throw new IllegalArgumentException("존재하지 않는 계좌입니다.");
            }
            if (e.getStatusCode().value() == 400) {
                // 400 Bad Request는 보통 잘못된 계좌번호나 은행코드
                throw new IllegalArgumentException("존재하지 않는 계좌입니다.");
            }
            throw new RuntimeException("외부 기관 통신 오류: " + e.getResponseBodyAsString(), e);

        } catch (Exception e) {
            log.error("계좌주명 조회 실패 - 은행코드: {}, 계좌번호: {}, 오류: {}", 
                     request.getBankCode(), request.getAccountNumber(), e.getMessage(), e);
            throw new RuntimeException("계좌주명 조회 중 오류가 발생했습니다.", e);
        }
    }
} 