package com.example.astrastudioopenai.service.tools.document;

import lombok.Getter;

@Getter
public class PdfOptions {
    public enum PageSize { A4, LETTER, A3 }
    
    private PageSize pageSize = PageSize.A4;
    private String margin = "25mm";
    private String fontFamily = "SimSun";
    private String headerText;
    private String footerText;
    private String watermark;

    public PdfOptions() {}

    public static PdfOptions defaults() {
        return new PdfOptions();
    }

    public static PdfOptions a4() {
        PdfOptions options = new PdfOptions();
        options.pageSize = PageSize.A4;
        return options;
    }

    public static PdfOptions letter() {
        PdfOptions options = new PdfOptions();
        options.pageSize = PageSize.LETTER;
        return options;
    }

    public PdfOptions setPageSize(PageSize pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    public PdfOptions setMargin(String margin) {
        this.margin = margin;
        return this;
    }

    public PdfOptions setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
        return this;
    }

    public PdfOptions setHeaderText(String headerText) {
        this.headerText = headerText;
        return this;
    }

    public PdfOptions setFooterText(String footerText) {
        this.footerText = footerText;
        return this;
    }

    public PdfOptions setWatermark(String watermark) {
        this.watermark = watermark;
        return this;
    }

    @Override
    public String toString() {
        return "PdfOptions{" +
                "pageSize=" + pageSize +
                ", margin='" + margin + '\'' +
                ", fontFamily='" + fontFamily + '\'' +
                ", hasHeader=" + (headerText != null) +
                ", hasFooter=" + (footerText != null) +
                ", hasWatermark=" + (watermark != null) +
                '}';
    }
}
