package io.ballerina.cli;

import io.ballerina.projects.Project;

import java.util.Map;

public abstract class PreBuildGenerator {

    Project project;

    PreBuildGenerator(Project project) {
        this.project = project;
    }
    abstract void executeTool();

    abstract String validateOptions(String id, String sourceValue, String sourceKind, String targetModule, Map<String, String> options);

    abstract String getToolName();

    final String getCachePath(String toolId) {
        return this.project.targetDir().resolve("cache").resolve(toolId).toAbsolutePath().toString();
    }

    final String getGeneratedModulePath(String targetModule) {
        return this.project.sourceRoot().resolve("generated").resolve(targetModule).toString();
    }

}
