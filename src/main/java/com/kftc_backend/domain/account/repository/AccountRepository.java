package com.kftc_backend.domain.account.repository;

import com.kftc_backend.domain.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {
    Optional<Account> findByAccountNumAndBankCode_BankCodeStd(String accountNum, String bankCodeStd);
} 