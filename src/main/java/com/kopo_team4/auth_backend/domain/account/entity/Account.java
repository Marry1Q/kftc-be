package com.kopo_team4.auth_backend.domain.account.entity;

import com.kopo_team4.auth_backend.domain.bank.entity.BankCode;
import com.kopo_team4.auth_backend.domain.user.entity.User;
import com.kopo_team4.auth_backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "auth_account")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Account extends BaseEntity {
    
    @Id
    @Column(name = "fintech_use_num", length = 50, nullable = false)
    private String fintechUseNum; // 핀테크 이용번호 (대문자 + 숫자 16자리)
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_seq_no", nullable = false)
    private User user; // 사용자 (외래키)
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_code_std", nullable = false)
    private BankCode bankCode; // 은행 코드 (외래키)
    
    @Column(name = "account_num", length = 50, nullable = false)
    private String accountNum; // 계좌번호
    
    @Column(name = "account_alias", length = 255)
    private String accountAlias; // 계좌별칭 (NULL 허용)
    
} 