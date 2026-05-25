package com.amman.fidel_ocr_engine.ocr;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
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

    // 1. TESSERACT ONLY
    public byte[] processAndConvertToDocx(MultipartFile file, String contentType) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            processFile(file, contentType, (img) -> {
                try {
                    return tesseract.doOCR(preprocessImage(img));
                } catch (TesseractException e) {
                    throw new RuntimeException(e);
                }
            }, document);
            return writeDocx(document);
        }
    }

    // 2. AI ONLY (100% AI)
    public byte[] processAndConvertToDocxWithAi(MultipartFile file, String contentType) throws Exception {
        StringBuilder fullText = new StringBuilder();
        processFile(file, contentType, (img) -> {
            try { return callAiForImage(img, "Transcribe this Ethiopic document accurately. Output ONLY the raw Ethiopic text. Do not include any explanations or intro/outro text."); }
            catch (Exception e) { return ""; }
        }, fullText);

        try (XWPFDocument document = new XWPFDocument()) {
            appendParagraphToDocx(document, fullText.toString());
            return writeDocx(document);
        }
    }

    // 3. HYBRID (TESSERACT + AI CORRECTION)
    public byte[] processAndConvertToDocxHybrid(MultipartFile file, String contentType) throws Exception {
        StringBuilder fullText = new StringBuilder();
        processFile(file, contentType, (img) -> {
            String raw = null;
            try {
                raw = tesseract.doOCR(preprocessImage(img));
            } catch (TesseractException e) {
                throw new RuntimeException(e);
            }
            return callAiForImageText("Correct this OCR text. Fix formatting and characters. Output ONLY the corrected text. Do NOT add meta-comments or explanations: " + raw);
        }, fullText);

        try (XWPFDocument document = new XWPFDocument()) {
            appendParagraphToDocx(document, fullText.toString());
            return writeDocx(document);
        }
    }

    // --- UTILITIES ---

    private void processFile(MultipartFile file, String contentType, java.util.function.Function<BufferedImage, String> processor, Object target) throws Exception {
        if (contentType != null && contentType.contains("application/pdf")) {
            try (PDDocument pdf = Loader.loadPDF(file.getBytes())) {
                PDFRenderer renderer = new PDFRenderer(pdf);
                for (int i = 0; i < pdf.getNumberOfPages(); i++) {
                    String result = processor.apply(renderer.renderImageWithDPI(i, 300));
                    if (target instanceof XWPFDocument) appendParagraphToDocx((XWPFDocument) target, result);
                    else ((StringBuilder) target).append(result).append("\n");
                }
            }
        } else {
            String result = processor.apply(ImageIO.read(file.getInputStream()));
            if (target instanceof XWPFDocument) appendParagraphToDocx((XWPFDocument) target, result);
            else ((StringBuilder) target).append(result);
        }
    }

    private String callAiForImage(BufferedImage image, String prompt) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpeg", baos);
            Media media = new Media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(baos.toByteArray()));
            return chatModel.call(new Prompt(UserMessage.builder().text(prompt).media(media).build())).getResult().getOutput().getText();
        }
    }

    private String callAiForImageText(String prompt) {
        return chatModel.call(new Prompt(UserMessage.builder().text(prompt).build())).getResult().getOutput().getText();
    }

    private byte[] writeDocx(XWPFDocument doc) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.write(out);
            return out.toByteArray();
        }
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

    private BufferedImage preprocessImage(BufferedImage src) {
        // Implementation of Otsu or standard grayscale/binarization
        return src;
    }
}