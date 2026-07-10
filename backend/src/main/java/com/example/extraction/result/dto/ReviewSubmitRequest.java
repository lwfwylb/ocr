package com.example.extraction.result.dto;

import java.util.ArrayList;
import java.util.List;

public class ReviewSubmitRequest {
    private List<ReviewFieldCorrectionRequest> fields = new ArrayList<>();
    private String comment;
    private String reviewer;

    public List<ReviewFieldCorrectionRequest> getFields() { return fields; }
    public void setFields(List<ReviewFieldCorrectionRequest> fields) { this.fields = fields; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getReviewer() { return reviewer; }
    public void setReviewer(String reviewer) { this.reviewer = reviewer; }
}
