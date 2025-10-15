package com.kftc_backend.domain.insurance.service;

import com.kftc_backend.domain.bank.entity.BankCode;
import com.kftc_backend.domain.bank.repository.BankCodeRepository;
import com.kftc_backend.domain.insurance.dto.request.ExternalInsuRequest;
import com.kftc_backend.domain.insurance.dto.request.InsuranceListRequest;
import com.kftc_backend.domain.insurance.dto.response.ExternalInsuResponse;
import com.kftc_backend.domain.insurance.dto.response.InsuranceListResponse;
import com.kftc_backend.domain.user.entity.User;
import com.kftc_backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InsuranceService {

    private final UserRepository userRepository;
    private final BankCodeRepository bankCodeRepository;
    private final RestTemplate restTemplate;

    public InsuranceListResponse getInsuranceList(InsuranceListRequest request) {
        // 1. userSeqNo로 User를 찾아 userCi 조회
        User user = userRepository.findById(request.getUserSeqNo())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. userSeqNo: " + request.getUserSeqNo()));
        String userCi = user.getUserCi();

        // 2. bankCodeStd로 BankCode를 찾아 endpoint 조회
        BankCode bankCode = bankCodeRepository.findById(request.getBankCodeStd())
                .orElseThrow(() -> new IllegalArgumentException("은행(보험사) 코드를 찾을 수 없습니다. bankCodeStd: " + request.getBankCodeStd()));
        String externalApiUrl = bankCode.getBankEndpoint() + "/api/v1/contracts/all";

        // 3. 외부 기관 API 호출
        ExternalInsuRequest externalRequest = new ExternalInsuRequest(userCi);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ExternalInsuRequest> entity = new HttpEntity<>(externalRequest, headers);

        ExternalInsuResponse externalResponse = restTemplate.postForObject(externalApiUrl, entity, ExternalInsuResponse.class);

        if (externalResponse == null || !externalResponse.isSuccess()) {
            throw new RuntimeException("외부 보험사 API 호출에 실패했습니다.");
        }

        // 4. 외부 응답을 우리 API 응답 형식으로 변환
        return InsuranceListResponse.builder()
                .insuCnt(externalResponse.getInsuCnt())
                .insuList(externalResponse.getData().stream().map(data ->
                        InsuranceListResponse.InsuInfo.builder()
                                .insuNum(data.getInsuNum())
                                .prodName(data.getProductName())
                                .insuType(data.getInsuType())
                                .insuranceCompany(data.getInsuranceCompany())
                                .insuStatus(data.getInsuStatus())
                                .issueDate(data.getIssueDate().replaceAll("-", ""))
                                .expDate(data.getExpDate().replaceAll("-", ""))
                                .build()
                ).collect(Collectors.toList()))
                .build();
    }
} 