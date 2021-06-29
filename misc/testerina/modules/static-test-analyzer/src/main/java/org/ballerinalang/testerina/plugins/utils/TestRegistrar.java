package org.ballerinalang.testerina.plugins.utils;

import io.ballerina.projects.plugins.CodeAnalysisContext;
import io.ballerina.projects.plugins.CodeAnalyzer;

/**
 * A code analyzer used to register already defined tests.
 *
 * @since 2.0.0
 */
public class TestRegistrar extends CodeAnalyzer {
    @Override
    public void init(CodeAnalysisContext analysisContext) {
        analysisContext.addCompilationAnalysisTask(new TestRegistrationTask());
    }
}
