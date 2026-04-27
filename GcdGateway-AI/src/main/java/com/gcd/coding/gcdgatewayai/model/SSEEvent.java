package com.gcd.coding.gcdgatewayai.model;

import lombok.Data;

@Data
public class SSEEvent {
    private String id;
    private String event;
    private String data;
    private Integer retry;

    public String toSSEString() {
        StringBuilder sb = new StringBuilder();
        if (id != null) {
            sb.append("id:").append(id).append("\n");
        }
        if (event != null) {
            sb.append("event:").append(event).append("\n");
        }
        if (data != null) {
            sb.append("data:").append(data).append("\n");
        }
        if (retry != null) {
            sb.append("retry:").append(retry).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    public static SSEEvent parse(String raw) {
        SSEEvent event = new SSEEvent();
        String[] lines = raw.split("\n");
        for (String line : lines) {
            if (line.startsWith("id:")) {
                event.id = line.substring(3).trim();
            } else if (line.startsWith("event:")) {
                event.event = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                event.data = line.substring(5).trim();
            } else if (line.startsWith("retry:")) {
                try {
                    event.retry = Integer.parseInt(line.substring(6).trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return event;
    }
}
