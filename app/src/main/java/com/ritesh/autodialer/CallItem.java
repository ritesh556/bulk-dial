package com.ritesh.autodialer;

public class CallItem {
    public String name;
    public String phone;
    public CallStatus status = CallStatus.PENDING;
    public int attempts = 0;
    public String note = "";

    public CallItem(String name, String phone) {
        this.name = name == null ? "" : name.trim();
        this.phone = phone == null ? "" : phone.trim();
    }

    public String displayName() {
        if (name == null || name.trim().isEmpty()) return phone;
        return name + "  " + phone;
    }
}
