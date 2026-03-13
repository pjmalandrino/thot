package com.example.chatinterface.document;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/conversations/{conversationId}/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(
            @PathVariable Long conversationId,
            @RequestParam("file") MultipartFile file) {
        return DocumentResponse.from(
                documentService.upload(conversationId, file));
    }

    @GetMapping
    public List<DocumentResponse> list(@PathVariable Long conversationId) {
        return documentService.getDocuments(conversationId).stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long conversationId,
                       @PathVariable Long documentId) {
        documentService.delete(conversationId, documentId);
    }
}
