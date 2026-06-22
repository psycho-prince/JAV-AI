package com.javai.models;

public class ReportSection {
    private int id;
    private int reportId;
    private int findingId;
    private int sortOrder;

    public ReportSection(int id, int reportId, int findingId, int sortOrder) {
        this.id = id;
        this.reportId = reportId;
        this.findingId = findingId;
        this.sortOrder = sortOrder;
    }

    public int getId() { return id; }
    public int getReportId() { return reportId; }
    public int getFindingId() { return findingId; }
    public int getSortOrder() { return sortOrder; }
}
