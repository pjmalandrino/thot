package com.example.chatinterface.googledrive;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoogleDriveTokenRepository extends JpaRepository<GoogleDriveToken, Long> {

    Optional<GoogleDriveToken> findByUserId(String userId);

    void deleteByUserId(String userId);

    boolean existsByUserId(String userId);
}
