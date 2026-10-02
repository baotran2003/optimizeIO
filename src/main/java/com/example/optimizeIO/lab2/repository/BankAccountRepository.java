package com.example.optimizeIO.lab2.repository;

import com.example.optimizeIO.lab2.entity.BankAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    // 1. Đọc thông thường (KHÔNG KHÓA) - Dùng để test lỗi Lost Update
    @Query("SELECT a FROM BankAccount a WHERE a.id = :id")
    Optional<BankAccount> findByIdNoLock(@Param("id") Long id);

    // 2. VŨ KHÍ PESSIMISTIC LOCK: Sinh ra câu SQL "SELECT ... FOR UPDATE"
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM BankAccount a WHERE a.id = :id")
    Optional<BankAccount> findByIdWithPessimisticLock(@Param("id") Long id);
}