package com.example.chatinterface.document;

import com.example.chatinterface.conversation.Conversation;
import com.example.chatinterface.conversation.ConversationRepository;
import com.example.chatinterface.thotspace.Thotspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static com.example.chatinterface.TestEntityHelper.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private DocumentGateway documentGateway;

    private DocumentService documentService;

    private Thotspace space;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(
                documentRepository, conversationRepository, documentGateway, 500);

        space = new Thotspace("Test");
        setId(space, 1L);

        conversation = new Conversation("Test conv", space);
        setId(conversation, 10L);
    }

    // ── upload ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("upload")
    class Upload {

        @Test
        @DisplayName("parse le fichier et sauvegarde le document")
        void uploadSuccess() {
            MultipartFile file = mock(MultipartFile.class);
            when(file.getOriginalFilename()).thenReturn("rapport.pdf");
            when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
            when(documentGateway.parse(file)).thenReturn(
                    new DocumentParseResult("rapport.pdf", "application/pdf", 5, 1200, "Contenu extrait du PDF")
            );
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
                Document d = inv.getArgument(0);
                setId(d, 1L);
                return d;
            });

            Document result = documentService.upload(10L, file);

            assertThat(result.getFilename()).isEqualTo("rapport.pdf");
            assertThat(result.getContentType()).isEqualTo("application/pdf");
            assertThat(result.getPageCount()).isEqualTo(5);
            assertThat(result.getCharCount()).isEqualTo(1200);
            assertThat(result.getExtractedText()).isEqualTo("Contenu extrait du PDF");
            verify(documentGateway).parse(file);
            verify(documentRepository).save(any(Document.class));
        }

        @Test
        @DisplayName("lance une exception si la conversation n'existe pas")
        void uploadConversationNotFound() {
            MultipartFile file = mock(MultipartFile.class);
            when(conversationRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> documentService.upload(999L, file))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Conversation not found");
        }
    }

    // ── delete ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("supprime un document appartenant a la conversation")
        void deleteSuccess() {
            Document doc = new Document(conversation, "file.pdf", "application/pdf", 1, 100, "text");
            setId(doc, 5L);
            when(documentRepository.findById(5L)).thenReturn(Optional.of(doc));

            documentService.delete(10L, 5L);

            verify(documentRepository).delete(doc);
        }

        @Test
        @DisplayName("lance une exception si le document n'appartient pas a la conversation")
        void deleteMismatch() {
            Conversation otherConv = new Conversation("Other", space);
            setId(otherConv, 99L);
            Document doc = new Document(otherConv, "file.pdf", "application/pdf", 1, 100, "text");
            setId(doc, 5L);
            when(documentRepository.findById(5L)).thenReturn(Optional.of(doc));

            assertThatThrownBy(() -> documentService.delete(10L, 5L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("does not belong");
        }

        @Test
        @DisplayName("lance une exception si le document n'existe pas")
        void deleteNotFound() {
            when(documentRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> documentService.delete(10L, 999L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Document not found");
        }
    }

    // ── buildDocumentContext ─────────────────────────────────────────────

    @Nested
    @DisplayName("buildDocumentContext")
    class BuildDocumentContext {

        @Test
        @DisplayName("retourne null si aucun document n'est attache")
        void emptyDocuments() {
            when(documentRepository.findByConversationIdOrderByUploadedAtAsc(10L))
                    .thenReturn(List.of());

            String result = documentService.buildDocumentContext(10L);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("formate le contexte avec les noms de documents")
        void formatsContext() {
            Document doc1 = new Document(conversation, "rapport.pdf", "application/pdf", 3, 200, "Premier contenu");
            Document doc2 = new Document(conversation, "notes.txt", "text/plain", null, 50, "Deuxieme contenu");
            when(documentRepository.findByConversationIdOrderByUploadedAtAsc(10L))
                    .thenReturn(List.of(doc1, doc2));

            String result = documentService.buildDocumentContext(10L);

            assertThat(result)
                    .contains("### Document 1 : rapport.pdf")
                    .contains("Premier contenu")
                    .contains("### Document 2 : notes.txt")
                    .contains("Deuxieme contenu");
        }

        @Test
        @DisplayName("tronque le contenu au-dela de maxContentLength")
        void truncatesLongContent() {
            String longText = "x".repeat(1000);
            Document doc = new Document(conversation, "big.pdf", "application/pdf", 10, 1000, longText);
            when(documentRepository.findByConversationIdOrderByUploadedAtAsc(10L))
                    .thenReturn(List.of(doc));

            String result = documentService.buildDocumentContext(10L);

            assertThat(result).contains("[...contenu tronque]");
            // Le contenu doit etre tronque a maxContentLength (500)
            assertThat(result).doesNotContain("x".repeat(501));
        }
    }

    // ── getDocuments ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getDocuments")
    class GetDocuments {

        @Test
        @DisplayName("retourne les documents d'une conversation")
        void returnsDocuments() {
            Document doc = new Document(conversation, "file.pdf", "application/pdf", 1, 100, "text");
            when(documentRepository.findByConversationIdOrderByUploadedAtAsc(10L))
                    .thenReturn(List.of(doc));

            List<Document> result = documentService.getDocuments(10L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getFilename()).isEqualTo("file.pdf");
        }
    }

}
