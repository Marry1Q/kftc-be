package com.kopo_team4.auth_backend.domain.client.repository;

import com.kopo_team4.auth_backend.domain.client.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientRepository extends JpaRepository<Client, String> {
} 