package com.cani.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class ResourceNotFoundException extends AppException {

    private static final Map<String, String> RESOURCE_NAME_VI = Map.ofEntries(
            Map.entry("User", "Người dùng"),
            Map.entry("Worker", "Nhân sự"),
            Map.entry("EventOrganizer", "Ban tổ chức"),
            Map.entry("Event", "Sự kiện"),
            Map.entry("Shift", "Ca làm việc"),
            Map.entry("ShiftAssignment", "Phân công ca làm"),
            Map.entry("DispatchProposal", "Đề xuất việc làm"),
            Map.entry("WorkerGroup", "Nhóm nhân sự"),
            Map.entry("WageMatrix", "Bảng mức lương")
    );

    private static final Map<String, String> FIELD_NAME_VI = Map.of(
            "id", "mã",
            "userId", "mã người dùng",
            "workerId", "mã nhân sự",
            "eoId", "mã ban tổ chức",
            "shiftId", "mã ca làm",
            "eventId", "mã sự kiện",
            "inviteCode", "mã mời"
    );

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s không tồn tại với %s: '%s'",
                RESOURCE_NAME_VI.getOrDefault(resourceName, resourceName),
                FIELD_NAME_VI.getOrDefault(fieldName, fieldName),
                fieldValue), HttpStatus.NOT_FOUND);
    }

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
