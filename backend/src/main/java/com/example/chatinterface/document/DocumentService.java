package com.example.chatinterface.document;

import com.example.chatinterface.conversation.Conversation;
import com.example.chatinterface.conversation.ConversationRepository;
import com.example.chatinterface.shared.exception.ResourceNotFoundException;
import com.example.chatinterface.shared.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final ConversationRepository conversationRepository;
    private final DocumentGateway documentGateway;
    private final int maxContentLength;

    public DocumentService(DocumentRepository documentRepository,
                           ConversationRepository conversationRepository,
                           DocumentGateway documentGateway,
                           @Value("${document.max-content-length:8000}") int maxContentLength) {
        this.documentRepository = documentRepository;
        this.conversationRepository = conversationRepository;
        this.documentGateway = documentGateway;
        this.maxContentLength = maxContentLength;
    }

    public Document upload(Long conversationId, MultipartFile file) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        log.info("[DOCUMENT] Parsing document '{}' for conversation {}",
                file.getOriginalFilename(), conversationId);

        DocumentParseResult result = documentGateway.parse(file);

        Document document = new Document(
                conversation,
                result.getFilename(),
                result.getContentType(),
                result.getPageCount(),
                result.getCharCount(),
                result.getExtractedText()
        );

        Document saved = documentRepository.save(document);
        log.info("[DOCUMENT] Saved document id={} for conversation {}", saved.getId(), conversationId);
        return saved;
    }

    public List<Document> getDocuments(Long conversationId) {
        return documentRepository.findByConversationIdOrderByUploadedAtAsc(conversationId);
    }

    @Transactional
    public void delete(Long conversationId, Long documentId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        if (!doc.getConversation().getId().equals(conversationId)) {
            throw new ValidationException("Document does not belong to this conversation");
        }
        documentRepository.delete(doc);
    }

    /**
     * Construit le bloc de contexte documentaire pour injection dans le system prompt.
     * Retourne null si aucun document n'est attache a la conversation.
     */
    public String buildDocumentContext(Long conversationId) {
        List<Document> docs = documentRepository
                .findByConversationIdOrderByUploadedAtAsc(conversationId);
        if (docs.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            sb.append("### Document ").append(i + 1).append(" : ")
              .append(doc.getFilename()).append("\n");

            String text = doc.getExtractedText();
            if (text == null || text.isEmpty()) {
                sb.append("(contenu non disponible)\n\n");
                continue;
            }
            if (text.length() > maxContentLength) {
                text = text.substring(0, maxContentLength) + "\n[...contenu tronque]";
            }
            sb.append(text).append("\n\n");
        }
        return sb.toString();
    }
}
