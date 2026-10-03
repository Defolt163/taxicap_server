package com.example.demo.WebSocket.repository;

import com.example.demo.WebSocket.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Integer> {
}