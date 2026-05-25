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

    // 1. TESSERACT ONLY (The deterministic baseline)
    @PostMapping(value = "/tesseract-only", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> convertTesseractOnly(@RequestParam("file") MultipartFile file) {
        return execute(file, "TESSERACT");
    }

    // 2. AI ONLY (The pure vision-based transcription)
    @PostMapping(value = "/ai-only", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> convertAiOnly(@RequestParam("file") MultipartFile file) {
        return execute(file, "AI");
    }

    // 3. HYBRID (The Tesseract + AI correction pipeline)
    @PostMapping(value = "/hybrid", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> convertHybrid(@RequestParam("file") MultipartFile file) {
        return execute(file, "HYBRID");
    }

    /**
     * Internal executor to avoid code duplication across the 3 endpoints.
     */
    private ResponseEntity<?> execute(MultipartFile file, String strategy) {
        if (file.isEmpty()) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("File is empty");

        try {
            byte[] bytes;
            String contentType = file.getContentType();

            switch (strategy) {
                case "TESSERACT" -> bytes = ocrService.processAndConvertToDocx(file, contentType);
                case "AI" -> bytes = ocrService.processAndConvertToDocxWithAi(file, contentType);
                case "HYBRID" -> bytes = ocrService.processAndConvertToDocxHybrid(file, contentType);
                default -> throw new IllegalArgumentException("Unknown strategy");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
            headers.setContentDispositionFormData("attachment", "fidel_" + strategy.toLowerCase() + ".docx");
            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(strategy + " failed: " + e.getMessage());
        }
    }
}