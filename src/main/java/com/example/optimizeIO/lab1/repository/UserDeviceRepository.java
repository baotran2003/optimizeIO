package com.example.optimizeIO.lab1.repository;

import com.example.optimizeIO.lab1.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    @Query("SELECT ud.userId, ud.deviceToken, ud.osType FROM UserDevice ud")
    List<Object[]> findAllDeviceProjections();
}
