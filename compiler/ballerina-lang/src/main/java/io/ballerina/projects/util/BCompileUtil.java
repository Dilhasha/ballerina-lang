/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.ballerina.projects.util;

import io.ballerina.projects.JBallerinaBackend;
import io.ballerina.projects.JvmTarget;
import io.ballerina.projects.Package;
import io.ballerina.projects.PackageCompilation;

/**
 * Utility methods for compile Ballerina files.
 *
 */
public class BCompileUtil {

    private static JBallerinaBackend jBallerinaBackend(Package currentPackage) {
        long start = System.currentTimeMillis();
        PackageCompilation packageCompilation = currentPackage.getCompilation();
        System.out.println("packageCompilationDuration: " + (System.currentTimeMillis() - start));
        if (packageCompilation.diagnosticResult().errorCount() > 0) {
            ContentServer.getInstance().sendMessage("Compilation failed with errors: " +
                    currentPackage.project().sourceRoot());
            return null;
        }
        start = System.currentTimeMillis();
        JBallerinaBackend jBallerinaBackend = JBallerinaBackend.from(packageCompilation, JvmTarget.JAVA_11);
        BRunUtil.jarLibraries = jBallerinaBackend.jarResolver().getJarFilePathsRequiredForExecution();
        BRunUtil.jBallerinaBackend = jBallerinaBackend;
        System.out.println("codeGenDuration: " + (System.currentTimeMillis() - start));
        return jBallerinaBackend;
    }

    public static CompileResult compile(Package currentPackage) {
        JBallerinaBackend jBallerinaBackend = jBallerinaBackend(currentPackage);
        if(jBallerinaBackend != null) {
            if (jBallerinaBackend.diagnosticResult().hasErrors()) {
                return new CompileResult(currentPackage, jBallerinaBackend);
            }
            CompileResult compileResult = new CompileResult(currentPackage, jBallerinaBackend);
            return compileResult;
        }
        return null;
    }

}
