package com.example.astrastudioopenai.service.tools;

import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

@Getter
public class ToolInfo {
    private final String name;
    private final String description;
    private final String className;
    private final Instant registeredAt;

    public ToolInfo(String name, String description, String className, Instant registeredAt) {
        this.name = name;
        this.description = description;
        this.className = className;
        this.registeredAt = registeredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ToolInfo toolInfo = (ToolInfo) o;
        return Objects.equals(name, toolInfo.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "ToolInfo{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", className='" + className + '\'' +
                ", registeredAt=" + registeredAt +
                '}';
    }
}
