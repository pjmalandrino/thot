package com.example.contextengine.infrastructure.adapter.in.rest.dto;

import com.example.contextengine.domain.model.ConversationMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingRequestDtoTest {

    // ── toDomainHistory() ────────────────────────────────────────────────────

    @Nested
    @DisplayName("toDomainHistory()")
    class ToDomainHistory {

        @Test
        @DisplayName("retourne une liste vide si conversationHistory est null")
        void nullHistory() {
            var dto = new StreamingRequestDto("prompt", null, null, null, null);
            assertThat(dto.toDomainHistory()).isEmpty();
        }

        @Test
        @DisplayName("retourne une liste vide si conversationHistory est vide")
        void emptyHistory() {
            var dto = new StreamingRequestDto("prompt", null, null, List.of(), null);
            assertThat(dto.toDomainHistory()).isEmpty();
        }

        @Test
        @DisplayName("convertit les items valides en ConversationMessage")
        void validItems() {
            var items = List.of(
                    new StreamingRequestDto.ConversationMessageItem("user", "Bonjour"),
                    new StreamingRequestDto.ConversationMessageItem("assistant", "Salut"));
            var dto = new StreamingRequestDto("prompt", null, null, items, null);

            List<ConversationMessage> result = dto.toDomainHistory();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).role()).isEqualTo("user");
            assertThat(result.get(0).content()).isEqualTo("Bonjour");
            assertThat(result.get(1).role()).isEqualTo("assistant");
            assertThat(result.get(1).content()).isEqualTo("Salut");
        }

        @Test
        @DisplayName("filtre les items null")
        void filtersNullItems() {
            List<StreamingRequestDto.ConversationMessageItem> items = new java.util.ArrayList<>();
            items.add(new StreamingRequestDto.ConversationMessageItem("user", "Bonjour"));
            items.add(null);
            var dto = new StreamingRequestDto("prompt", null, null, items, null);

            assertThat(dto.toDomainHistory()).hasSize(1);
        }

        @Test
        @DisplayName("filtre les items avec role null")
        void filtersNullRole() {
            var items = List.of(
                    new StreamingRequestDto.ConversationMessageItem(null, "content"),
                    new StreamingRequestDto.ConversationMessageItem("user", "valid"));
            var dto = new StreamingRequestDto("prompt", null, null, items, null);

            assertThat(dto.toDomainHistory()).hasSize(1);
            assertThat(dto.toDomainHistory().get(0).role()).isEqualTo("user");
        }

        @Test
        @DisplayName("filtre les items avec content null")
        void filtersNullContent() {
            var items = List.of(
                    new StreamingRequestDto.ConversationMessageItem("user", null),
                    new StreamingRequestDto.ConversationMessageItem("user", "valid"));
            var dto = new StreamingRequestDto("prompt", null, null, items, null);

            assertThat(dto.toDomainHistory()).hasSize(1);
            assertThat(dto.toDomainHistory().get(0).content()).isEqualTo("valid");
        }
    }

    // ── isWebSearchRequestedOrDefault() ──────────────────────────────────────

    @Nested
    @DisplayName("isWebSearchRequestedOrDefault()")
    class IsWebSearchRequestedOrDefault {

        @Test
        @DisplayName("retourne false quand webSearchRequested est null")
        void nullReturnsFalse() {
            var dto = new StreamingRequestDto("prompt", null, null, null, null);
            assertThat(dto.isWebSearchRequestedOrDefault()).isFalse();
        }

        @Test
        @DisplayName("retourne true quand webSearchRequested est true")
        void trueReturnsTrue() {
            var dto = new StreamingRequestDto("prompt", null, null, null, true);
            assertThat(dto.isWebSearchRequestedOrDefault()).isTrue();
        }

        @Test
        @DisplayName("retourne false quand webSearchRequested est false")
        void falseReturnsFalse() {
            var dto = new StreamingRequestDto("prompt", null, null, null, false);
            assertThat(dto.isWebSearchRequestedOrDefault()).isFalse();
        }
    }
}
