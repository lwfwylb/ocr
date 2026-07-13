package com.example.extraction.trace.dto;

import com.example.extraction.artifact.dto.DocumentArtifactResponse;
import com.example.extraction.artifact.dto.DocumentArtifactStepResponse;
import com.example.extraction.document.dto.DocumentAccessResponse;
import com.example.extraction.result.dto.ResultDetailResponse;
import com.example.extraction.result.dto.PushRecordResponse;
import com.example.extraction.result.dto.ReviewLogResponse;
import com.example.extraction.result.dto.StorageRecordResponse;
import com.example.extraction.task.dto.TaskResponse;

import java.util.ArrayList;
import java.util.List;

public class TraceDetailResponse {
    private TraceSummaryResponse summary;
    private DocumentAccessResponse accessRecord;
    private TaskResponse task;
    private ResultDetailResponse result;
    private StorageRecordResponse storageRecord;
    private List<PushRecordResponse> pushRecords = new ArrayList<>();
    private List<TraceStageResponse> stages = new ArrayList<>();
    private List<DocumentArtifactResponse> artifacts = new ArrayList<>();
    private List<DocumentArtifactStepResponse> artifactSteps = new ArrayList<>();
    private List<ReviewLogResponse> reviewLogs = new ArrayList<>();
    private List<String> suggestions = new ArrayList<>();

    public TraceSummaryResponse getSummary() { return summary; }
    public void setSummary(TraceSummaryResponse summary) { this.summary = summary; }
    public DocumentAccessResponse getAccessRecord() { return accessRecord; }
    public void setAccessRecord(DocumentAccessResponse accessRecord) { this.accessRecord = accessRecord; }
    public TaskResponse getTask() { return task; }
    public void setTask(TaskResponse task) { this.task = task; }
    public ResultDetailResponse getResult() { return result; }
    public void setResult(ResultDetailResponse result) { this.result = result; }
    public StorageRecordResponse getStorageRecord() { return storageRecord; }
    public void setStorageRecord(StorageRecordResponse storageRecord) { this.storageRecord = storageRecord; }
    public List<PushRecordResponse> getPushRecords() { return pushRecords; }
    public void setPushRecords(List<PushRecordResponse> pushRecords) { this.pushRecords = pushRecords; }
    public List<TraceStageResponse> getStages() { return stages; }
    public void setStages(List<TraceStageResponse> stages) { this.stages = stages; }
    public List<DocumentArtifactResponse> getArtifacts() { return artifacts; }
    public void setArtifacts(List<DocumentArtifactResponse> artifacts) { this.artifacts = artifacts; }
    public List<DocumentArtifactStepResponse> getArtifactSteps() { return artifactSteps; }
    public void setArtifactSteps(List<DocumentArtifactStepResponse> artifactSteps) { this.artifactSteps = artifactSteps; }
    public List<ReviewLogResponse> getReviewLogs() { return reviewLogs; }
    public void setReviewLogs(List<ReviewLogResponse> reviewLogs) { this.reviewLogs = reviewLogs; }
    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }
}
