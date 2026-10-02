package com.example.optimizeIO.lab2.service.day3;

import com.example.optimizeIO.lab2.entity.BankAccount;
import com.example.optimizeIO.lab2.repository.BankAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class PessimisticLockService {

    private final BankAccountRepository accountRepository;

    /**
     * Khởi tạo tài khoản mẫu với số dư 100$
     */
    public Long setupAccount(long initialBalance) {
        accountRepository.deleteAll();
        BankAccount account = accountRepository.save(
                BankAccount.builder()
                        .accountNumber("account_01")
                        .balance(initialBalance)
                        .build()
        );
        log.info("[BankSetup] Đã tạo tài khoản ID: {}, số dư ban đầu: {}$", account.getId(), initialBalance);
        return account.getId();
    }

    /**
     * Bắn N luồng đồng thời, mỗi luồng rút amountPerWithdrawal
     * @param usePessimisticLock true: Dùng SELECT FOR UPDATE | false: Rút kiểu Naive
     *                           amountPerWithdrawal: số tiền rút trong 1 transaction
     */
    public Map<String, Object> simulateConcurrentWithdrawals(int concurrentThreads, long amountPerWithdrawal, boolean usePessimisticLock) {
        long initialBalance = 100L;
        Long accountId = setupAccount(initialBalance);

        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        CountDownLatch startGun = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 1; i <= concurrentThreads; i++) {
            final int threadId = i;
            futures.add(executor.submit(() -> {
                try {
                    startGun.await(); // Chờ súng lệnh
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                try {
                    if (usePessimisticLock) {
                        // TODO ①: Gọi hàm rút tiền có Pessimistic Lock
                        withdrawWithPessimisticLock(accountId, amountPerWithdrawal);
                    } else {
                        // TODO ②: Gọi hàm rút tiền Naive (không lock)
                        withdrawNaiveNoLock(accountId, amountPerWithdrawal);
                    }
                    successCount.incrementAndGet();
                    log.info("[Withdraw] Thread-{} rút {}$ thành công!", threadId, amountPerWithdrawal);
                } catch (Exception e) {
                    failedCount.incrementAndGet();
                    log.warn("[Withdraw] Thread-{} thất bại: {}", threadId, e.getMessage());
                }
            }));
        }

        // Bắn súng lệnh để các luồng cùng ùa vào rút tiền
        startGun.countDown();

        for (Future<?> f : futures) {
            try { f.get(10, TimeUnit.SECONDS); } catch (Exception ignored) {}
        }
        executor.shutdown();

        // Đọc lại số dư thực tế trong DB
        BankAccount finalAccount = accountRepository.findById(accountId).orElseThrow();
        long actualFinalBalance = finalAccount.getBalance();
        long expectedFinalBalance = initialBalance - (successCount.get() * amountPerWithdrawal);

        boolean isCorrect = (actualFinalBalance == expectedFinalBalance);

        return Map.of(
                "mode", usePessimisticLock ? "PESSIMISTIC_LOCK (SELECT FOR UPDATE)" : "NAIVE_NO_LOCK (RACE CONDITION)",
                "initialBalance", initialBalance,
                "withdrawPerThread", amountPerWithdrawal,
                "concurrentThreads", concurrentThreads,
                "successCount", successCount.get(),
                "failedCount", failedCount.get(),
                "actualFinalBalanceInDb", actualFinalBalance,
                "expectedFinalBalance", expectedFinalBalance,
                "isCorrect", isCorrect,
                "verdict", isCorrect
                        ? "✅ CHUẨN XÁC! Dữ liệu tiền không bị mất một xu!"
                        : "❌ LỖI LOST UPDATE! Số dư trong DB bị sai lệch do Race Condition!"
        );
    }

    /**
     * 1. Rút tiền KHÔNG KHÓA (Naive) - Dễ bị Lost Update
     */
    @Transactional
    public void withdrawNaiveNoLock(Long accountId, long amount) {
        // TODO ③: Đọc account bằng repository.findByIdNoLock(accountId)
        BankAccount account = accountRepository.findByIdNoLock(accountId).orElseThrow();

        if (account.getBalance() < amount) {
            throw new RuntimeException("Số dư không đủ!");
        }

        // Giả lập trễ xử lý 5ms để các luồng khác chen vào đọc đè
        try { Thread.sleep(5); } catch (InterruptedException ignored) {}

        // TODO ④: Trừ tiền và lưu lại
        account.setBalance(account.getBalance() - amount);
        accountRepository.save(account);
    }

    /**
     * 2. Rút tiền CÓ PESSIMISTIC LOCK (SELECT ... FOR UPDATE)
     */
    @Transactional
    public void withdrawWithPessimisticLock(Long accountId, long amount) {
        // TODO ⑤: Đọc account bằng repository.findByIdWithPessimisticLock(accountId)
        //          Lúc này PostgreSQL sẽ KHÓA CỨNG dòng này lại (Row-level Lock)!
        BankAccount account = accountRepository.findByIdWithPessimisticLock(accountId).orElseThrow();

        if (account.getBalance() < amount) {
            throw new RuntimeException("Số dư không đủ!");
        }

        // Dù có ngủ 5ms, các luồng khác cũng PHẢI XẾP HÀNG CHỜ ở cổng DB
        try { Thread.sleep(5); } catch (InterruptedException ignored) {}

        // TODO ⑥: Trừ tiền và lưu lại
        account.setBalance(account.getBalance() - amount);
        accountRepository.save(account);
    }
}