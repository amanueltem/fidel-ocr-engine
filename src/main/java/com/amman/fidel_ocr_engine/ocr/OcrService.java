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

        // --- UPDATED FOR YOUR TESS4J VERSION ---
        // 1. Use the direct integer-based page segmentation configuration method
        this.tesseract.setPageSegMode(3); // 3 = Fully automatic page segmentation, line-by-line

        // 2. Use setVariable instead of setTessVariable
        this.tesseract.setVariable("textord_no_chops", "T");
        this.tesseract.setVariable("preserve_interword_spaces", "1");
        this.tesseract.setVariable("user_defined_dpi", "300");
    }

    public byte[] processAndConvertToDocx(MultipartFile file, String contentType) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             InputStream is = file.getInputStream();
             PDDocument pdfDocument = Loader.loadPDF(is.readAllBytes())) {

            PDFRenderer pdfRenderer = new PDFRenderer(pdfDocument);

            for (int page = 0; page < pdfDocument.getNumberOfPages(); page++) {
                // Render with optimal 300 DPI for high fidelity
                BufferedImage rawImage = pdfRenderer.renderImageWithDPI(page, 300);

                // Pure black-and-white threshold filtering
                BufferedImage cleanedImage = preprocessImage(rawImage);

                String pageText = tesseract.doOCR(cleanedImage);

                appendParagraphToDocx(document, pageText);

                if (page < pdfDocument.getNumberOfPages() - 1) {
                    document.createParagraph().setPageBreak(true);
                }
            }

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                document.write(out);
                return out.toByteArray();
            }
        }
    }

    private BufferedImage preprocessImage(BufferedImage src) {
        BufferedImage grayImage = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = grayImage.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();

        BufferedImage binarizedImage = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        for (int x = 0; x < grayImage.getWidth(); x++) {
            for (int y = 0; y < grayImage.getHeight(); y++) {
                int rgb = grayImage.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;

                // Adaptive threshold cutoff to keep font edges smooth
                if (r > 185) {
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
            // Drop common edge-artifacts or scan border remnants
            if (cleanLine.isEmpty() || cleanLine.equals("|")) continue;

            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(cleanLine);
            run.setFontSize(12);
            run.setFontFamily("Abyssinica SIL");
        }
    }
}