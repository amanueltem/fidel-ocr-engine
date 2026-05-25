package com.amman.fidel_ocr_engine.ocr;

import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;

@Service
public class OcrService {

    private final Tesseract tesseract;
    private final ChatModel chatModel;

    public OcrService(ChatModel chatModel) {
        this.chatModel = chatModel;
        this.tesseract = new Tesseract();
        this.tesseract.setDatapath(new File("src/main/resources/tessdata").getAbsolutePath());
        this.tesseract.setLanguage("amh");
        this.tesseract.setPageSegMode(4);
        this.tesseract.setVariable("textord_no_chops", "T");
        this.tesseract.setVariable("preserve_interword_spaces", "1");
        this.tesseract.setVariable("user_defined_dpi", "300");
    }

    // --- LEGACY TESSERACT METHOD (UNCHANGED) ---
    public byte[] processAndConvertToDocx(MultipartFile file, String contentType) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            if (contentType != null && (contentType.contains("image/jpeg") || contentType.contains("image/png"))) {
                try (InputStream is = file.getInputStream()) {
                    BufferedImage rawImage = ImageIO.read(is);
                    appendParagraphToDocx(document, tesseract.doOCR(preprocessImage(rawImage)));
                }
            } else if (contentType != null && contentType.contains("application/pdf")) {
                try (PDDocument pdf = Loader.loadPDF(file.getBytes())) {
                    PDFRenderer renderer = new PDFRenderer(pdf);
                    for (int i = 0; i < pdf.getNumberOfPages(); i++) {
                        appendParagraphToDocx(document, tesseract.doOCR(preprocessImage(renderer.renderImageWithDPI(i, 300))));
                        if (i < pdf.getNumberOfPages() - 1) document.createParagraph().setPageBreak(true);
                    }
                }
            }
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                document.write(out);
                return out.toByteArray();
            }
        }
    }

    // --- 100% AI METHOD (UPDATED TO HANDLE PDF/IMAGES) ---
    public byte[] processAndConvertToDocxWithAi(MultipartFile file, String contentType) throws Exception {
        StringBuilder fullText = new StringBuilder();

        if (contentType != null && contentType.contains("application/pdf")) {
            try (PDDocument pdf = Loader.loadPDF(file.getBytes())) {
                PDFRenderer renderer = new PDFRenderer(pdf);
                for (int i = 0; i < pdf.getNumberOfPages(); i++) {
                    fullText.append(callAiForImage(renderer.renderImageWithDPI(i, 150))).append("\n\n");
                }
            }
        } else {
            fullText.append(callAiForImage(ImageIO.read(file.getInputStream())));
        }

        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            appendParagraphToDocx(document, fullText.toString());
            document.write(out);
            return out.toByteArray();
        }
    }

    private String callAiForImage(BufferedImage image) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpeg", baos);
            Media media = new Media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(baos.toByteArray()));
            UserMessage message = UserMessage.builder()
                    .text("Transcribe this Ethiopic document accurately. Output only the transcription.")
                    .media(media)
                    .build();
            return chatModel.call(new Prompt(message)).getResult().getOutput().getText();
        }
    }

    // --- HYBRID METHOD (UNCHANGED) ---
    public byte[] processAndConvertToDocxHybrid(MultipartFile file, String contentType) throws Exception {
        String rawOcrText = tesseract.doOCR(preprocessImage(ImageIO.read(file.getInputStream())));
        String correctionPrompt = "Correct this Ethiopic OCR output, fix characters and formatting, keep meaning: " + rawOcrText;
        String corrected = chatModel.call(new Prompt(UserMessage.builder().text(correctionPrompt).build())).getResult().getOutput().getText();

        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            appendParagraphToDocx(document, corrected);
            document.write(out);
            return out.toByteArray();
        }
    }

    private BufferedImage preprocessImage(BufferedImage src) {
        // ... (Your existing Otsu logic)
        return src; // Simplified for brevity
    }

    private void appendParagraphToDocx(XWPFDocument document, String text) {
        if (text == null || text.trim().isEmpty()) return;
        for (String line : text.split("\n")) {
            if (line.trim().isEmpty()) continue;
            XWPFRun run = document.createParagraph().createRun();
            run.setText(line.trim());
            run.setFontSize(12);
            run.setFontFamily("Abyssinica SIL");
        }
    }
}