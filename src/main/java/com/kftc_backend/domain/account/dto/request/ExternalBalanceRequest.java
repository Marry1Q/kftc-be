package com.kftc_backend.domain.account.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ExternalBalanceRequest {
    private String userCi;
    private String accountNum;
} 