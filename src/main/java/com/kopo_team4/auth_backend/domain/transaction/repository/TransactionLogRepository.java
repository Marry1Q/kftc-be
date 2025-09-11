package com.kopo_team4.auth_backend.domain.transaction.repository;

import com.kopo_team4.auth_backend.domain.transaction.entity.TransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionLogRepository extends JpaRepository<TransactionLog, Long> {
} 