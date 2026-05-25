package com.amman.fidel_ocr_engine.ocr;

import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;

@Service
public class OcrService {

    private final Tesseract tesseract;

    public OcrService() {
        this.tesseract = new Tesseract();
        this.tesseract.setDatapath(new File("src/main/resources/tessdata").getAbsolutePath());
        this.tesseract.setLanguage("amh");

        // PSM 4 dynamically clusters text lines of variable sizes, keeping form/memo fields ordered
        this.tesseract.setPageSegMode(4);

        this.tesseract.setVariable("textord_no_chops", "T");
        this.tesseract.setVariable("preserve_interword_spaces", "1");
        this.tesseract.setVariable("user_defined_dpi", "300");
    }

    public byte[] processAndConvertToDocx(MultipartFile file, String contentType) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {

            if (contentType != null && (contentType.contains("image/jpeg") || contentType.contains("image/png"))) {
                try (InputStream is = file.getInputStream()) {
                    BufferedImage rawImage = ImageIO.read(is);
                    if (rawImage == null) {
                        throw new IllegalArgumentException("The uploaded file could not be parsed as a valid image.");
                    }

                    BufferedImage cleanedImage = preprocessImage(rawImage);
                    String pageText = tesseract.doOCR(cleanedImage);

                    appendParagraphToDocx(document, pageText);
                }
            }
            else if (contentType != null && contentType.contains("application/pdf")) {
                try (InputStream is = file.getInputStream();
                     PDDocument pdfDocument = Loader.loadPDF(is.readAllBytes())) {

                    PDFRenderer pdfRenderer = new PDFRenderer(pdfDocument);

                    for (int page = 0; page < pdfDocument.getNumberOfPages(); page++) {
                        BufferedImage rawImage = pdfRenderer.renderImageWithDPI(page, 300);
                        BufferedImage cleanedImage = preprocessImage(rawImage);

                        String pageText = tesseract.doOCR(cleanedImage);
                        appendParagraphToDocx(document, pageText);

                        if (page < pdfDocument.getNumberOfPages() - 1) {
                            document.createParagraph().setPageBreak(true);
                        }
                    }
                }
            } else {
                throw new IllegalArgumentException("Unsupported file format: " + contentType);
            }

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                document.write(out);
                return out.toByteArray();
            }
        }
    }

    /**
     * Applies dynamic Otsu Binarization to cleanly extract text strokes
     * under variable office lighting conditions and document shadows.
     */
    private BufferedImage preprocessImage(BufferedImage src) {
        // Step 1: Grayscale Conversion
        BufferedImage grayImage = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = grayImage.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();

        // Step 2: Compute Histogram for Otsu's Algorithm
        int[] histogram = new int[256];
        for (int x = 0; x < grayImage.getWidth(); x++) {
            for (int y = 0; y < grayImage.getHeight(); y++) {
                int rgb = grayImage.getRGB(x, y);
                int grayValue = (rgb >> 16) & 0xFF;
                histogram[grayValue]++;
            }
        }

        // Step 3: Calculate optimal Otsu threshold automatically
        int totalPixels = src.getWidth() * src.getHeight();
        float sum = 0;
        for (int i = 0; i < 256; i++) sum += i * histogram[i];

        float sumB = 0;
        int wB = 0;
        int wF = 0;
        float varMax = 0;
        int optimalThreshold = 128; // Default fallback if variance matches uniformly

        for (int i = 0; i < 256; i++) {
            wB += histogram[i];
            if (wB == 0) continue;
            wF = totalPixels - wB;
            if (wF == 0) break;

            sumB += (float) (i * histogram[i]);
            float mB = sumB / wB;
            float mF = (sum - sumB) / wF;

            float varBetween = (float) wB * (float) wF * (mB - mF) * (mB - mF);
            if (varBetween > varMax) {
                varMax = varBetween;
                optimalThreshold = i;
            }
        }

        // Step 4: Map binary pixels using the dynamic calculated threshold
        BufferedImage binarizedImage = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        for (int x = 0; x < grayImage.getWidth(); x++) {
            for (int y = 0; y < grayImage.getHeight(); y++) {
                int rgb = grayImage.getRGB(x, y);
                int grayValue = (rgb >> 16) & 0xFF;

                if (grayValue > optimalThreshold) {
                    binarizedImage.setRGB(x, y, Color.WHITE.getRGB());
                } else {
                    binarizedImage.setRGB(x, y, Color.BLACK.getRGB());
                }
            }
        }
        return binarizedImage;
    }

    private void appendParagraphToDocx(XWPFDocument document, String text) {
        if (text == null || text.trim().isEmpty()) return;

        String[] lines = text.split("\n");
        for (String line : lines) {
            String cleanLine = line.trim();
            if (cleanLine.isEmpty() || cleanLine.equals("|")) continue;

            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(cleanLine);
            run.setFontSize(12);
            run.setFontFamily("Abyssinica SIL");
        }
    }
}