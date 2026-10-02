package com.example.optimizeIO.lab2.repository;

import com.example.optimizeIO.lab2.entity.CampaignExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignExecutionRepository extends JpaRepository<CampaignExecution, Long> {

    // TODO ①: Khai báo method kiểm tra chiến dịch đã tồn tại chưa theo campaignCode
    boolean existsByCampaignCode(String campaignCode);

    // TODO ②: Khai báo method đếm số bản ghi theo campaignCode
    long countByCampaignCode(String campaignCode);
}
