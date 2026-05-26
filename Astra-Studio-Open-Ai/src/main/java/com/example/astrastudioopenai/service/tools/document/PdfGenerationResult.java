package com.example.astrastudioopenai.service.tools.document;

import java.util.Objects;

public class PdfGenerationResult {
    private boolean success;
    private String message;
    private String fileName;
    private long fileSizeBytes;
    private int pageCount;
    private String pageSize;
    private String downloadUrl;

    public PdfGenerationResult() {
    }

    public PdfGenerationResult(boolean success, String message, String fileName,
                             long fileSizeBytes, int pageCount, String pageSize, String downloadUrl) {
        this.success = success;
        this.message = message;
        this.fileName = fileName;
        this.fileSizeBytes = fileSizeBytes;
        this.pageCount = pageCount;
        this.pageSize = pageSize;
        this.downloadUrl = downloadUrl;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }

    public String getPageSize() {
        return pageSize;
    }

    public void setPageSize(String pageSize) {
        this.pageSize = pageSize;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PdfGenerationResult that = (PdfGenerationResult) o;
        return success == that.success &&
                fileSizeBytes == that.fileSizeBytes &&
                pageCount == that.pageCount &&
                Objects.equals(fileName, that.fileName) &&
                Objects.equals(message, that.message) &&
                Objects.equals(pageSize, that.pageSize) &&
                Objects.equals(downloadUrl, that.downloadUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, message, fileName, fileSizeBytes, pageCount, pageSize, downloadUrl);
    }

    @Override
    public String toString() {
        return "PdfGenerationResult{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileSizeBytes=" + fileSizeBytes +
                ", pageCount=" + pageCount +
                ", pageSize='" + pageSize + '\'' +
                ", downloadUrl='" + downloadUrl + '\'' +
                '}';
    }
}
