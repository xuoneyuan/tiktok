package com.tiktok.service;

public interface AuditService<T,R> {
    R audit(T task);
}
