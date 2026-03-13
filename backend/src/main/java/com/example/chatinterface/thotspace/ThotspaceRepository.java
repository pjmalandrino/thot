package com.example.chatinterface.thotspace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ThotspaceRepository extends JpaRepository<Thotspace, Long> {
    List<Thotspace> findAllByOrderByCreatedAtAsc();
    Optional<Thotspace> findByIsDefaultTrue();
}
