package com.amman.fidel_ocr_engine.ocr;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/fidel-ocr")
public class OcrController {

    private final OcrService ocrService;

    public OcrController(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    /**
     * Accepts a scanned image or PDF, executes inference via the Ge'ez ONNX engine,
     * and streams a native, editable Microsoft Word (.docx) file back to the client.
     */
    @PostMapping(value = "/convert-to-word", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> convertScannedDocumentToWord(@RequestParam("file") MultipartFile file) {
        String contentType = file.getContentType();

        if (file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Uploaded payload cannot be empty.");
        }

        try {
            // Process the input document and compile the DOCX structure
            byte[] docxBytes = ocrService.processAndConvertToDocx(file, contentType);

            // Configure file download attachments headers explicitly
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
            headers.setContentDispositionFormData("attachment", "fidel_extracted_document.docx");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            return new ResponseEntity<>(docxBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            // Explicit tracking to standard output to trace logic failures in real time
            e.printStackTrace();

            // Re-throw the clean exception up so it hits your GlobalExceptionHandler flawlessly
            throw new RuntimeException("Fidel OCR Engine pipeline failed processing execution: " + e.getMessage(), e);
        }
    }
}