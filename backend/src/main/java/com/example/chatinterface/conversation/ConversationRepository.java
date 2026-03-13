package com.example.chatinterface.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    List<Conversation> findAllByOrderByCreatedAtDesc();
    List<Conversation> findByThotspaceIdOrderByCreatedAtDesc(Long thotspaceId);

    @Modifying
    @Query("UPDATE Conversation c SET c.thotspace.id = :targetId WHERE c.thotspace.id = :sourceId")
    void reassignThotspace(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);
}
