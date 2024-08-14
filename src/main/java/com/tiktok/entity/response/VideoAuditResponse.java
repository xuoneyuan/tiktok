package com.tiktok.entity.response;

import com.tiktok.entity.task.VideoTask;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class VideoAuditResponse {
    private AuditResponse videoAuditResponse;

    private AuditResponse imageAuditResponse;

    private AuditResponse textAuditResponse;

    private VideoTask videoTask;
}
