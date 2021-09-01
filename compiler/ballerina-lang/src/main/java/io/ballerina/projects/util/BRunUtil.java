/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.projects.util;

import io.ballerina.projects.JarResolver;
import io.ballerina.projects.PackageManifest;
import io.ballerina.projects.ProjectException;
import io.ballerina.runtime.api.PredefinedTypes;
import io.ballerina.runtime.internal.configurable.providers.toml.TomlDetails;
import io.ballerina.runtime.internal.launch.LaunchUtils;
import io.ballerina.runtime.internal.scheduling.Scheduler;
import io.ballerina.runtime.internal.scheduling.Strand;
import io.ballerina.runtime.internal.util.exceptions.BLangRuntimeException;
import io.ballerina.runtime.internal.values.ErrorValue;
import io.ballerina.runtime.internal.values.FutureValue;
import org.wso2.ballerinalang.compiler.bir.codegen.JvmCodeGenUtil;
import org.wso2.ballerinalang.compiler.desugar.ASTBuilderUtil;
import org.wso2.ballerinalang.compiler.tree.BLangIdentifier;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * Utility methods for run Ballerina functions.
 *
 * @since 2.0.0
 */
public class BRunUtil {

    private static final String MODULE_INIT_CLASS_NAME = "$_init";
    private static final String MAIN_CLASS_NAME = "main";
    private static final String CONFIGURATION_CLASS_NAME = "$configurationMapper";

    public static Object run(CompileResult compileResult)
            throws ClassNotFoundException, ProjectException {
        long start = System.currentTimeMillis();
        PackageManifest packageManifest = compileResult.packageManifest();
        String org = packageManifest.org().toString();
        String module = packageManifest.name().toString();
        String version = packageManifest.version().toString();
        String initClassName = JarResolver.getQualifiedClassName(org, module, version, MODULE_INIT_CLASS_NAME);
        String mainClassName = JarResolver.getQualifiedClassName(org, module, version, MAIN_CLASS_NAME);
        String configClassName = JarResolver.getQualifiedClassName(org, module, version, CONFIGURATION_CLASS_NAME);

        Class<?> initClazz = compileResult.getClassLoader().loadClass(initClassName);
        Class<?> mainClazz = compileResult.getClassLoader().loadClass(mainClassName);
        final Scheduler scheduler = new Scheduler(false);
        TomlDetails configurationDetails = LaunchUtils.getConfigurationDetails();
        directRun(compileResult.getClassLoader().loadClass(configClassName), "$configureInit",
                new Class[]{String[].class, Path[].class, String.class}, new Object[]{new String[]{},
                        configurationDetails.paths, configurationDetails.configContent});
        try {
            Object output1 = runOnSchedule(initClazz, ASTBuilderUtil.createIdentifier(null, "$moduleInit"), scheduler);
            Object output2 = runOnSchedule(mainClazz, ASTBuilderUtil.createIdentifier(null, "main"), scheduler);
            Object output3 = runOnSchedule(mainClazz, ASTBuilderUtil.createIdentifier(null, "$moduleStart"), scheduler);
            String output = "";
            if(output1 instanceof String){
                output = output.concat((String)output1);
            }
            if(output2 instanceof String){
                output = output.concat((String)output2);
            }
            if(output3 instanceof String){
                output = output.concat((String)output3);
            }
            System.out.println("ProgramExecutionDuration: " + (System.currentTimeMillis() - start));
            return output;
        }catch(Exception exception){
            return exception;
        }
    }

    private static Object runOnSchedule(Class<?> initClazz, BLangIdentifier name, Scheduler scheduler) {
        String funcName = JvmCodeGenUtil.cleanupFunctionName(name.value);
        try {
            final Method method = initClazz.getDeclaredMethod(funcName, Strand.class);
            // Create a stream to hold the output
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            PrintStream printStream = new PrintStream(byteArrayOutputStream);
            PrintStream existingStream = System.out;
            // Use defined stream
            System.setOut(printStream);
            //TODO fix following method invoke to scheduler.schedule()
            Function<Object[], Object> func = objects -> {
                try {
                    Object response = method.invoke(null, objects[0]);
                    System.out.flush();
                    System.setOut(existingStream);
                    return response;
                } catch (InvocationTargetException e) {
                    Throwable targetException = e.getTargetException();
                    if (targetException instanceof RuntimeException) {
                        return (RuntimeException) targetException;
                    } else {
                        return new RuntimeException(targetException);
                    }
                } catch (IllegalAccessException e) {
                    throw new ProjectException("Method has private access", e);
                }
            };
            final FutureValue out = scheduler
                    .schedule(new Object[1], func, null, null, null, PredefinedTypes.TYPE_ANY, null, null);
            scheduler.start();
            if (byteArrayOutputStream.size() > 0) {
                return byteArrayOutputStream.toString();
            }
            final Throwable t = out.getPanic();
            if (t != null) {
                if (t instanceof BLangRuntimeException) {
                    return new ProjectException(t.getMessage());
                }
                if (t instanceof ErrorValue) {
                    return new ProjectException(
                            "error: " + ((ErrorValue) t).getPrintableStackTrace());
                }
                return t;
            } else{
                return out.getResult();
            }

        } catch (NoSuchMethodException e) {
            return new RuntimeException("Error while invoking function '" + funcName + "'", e);
        }
    }

    private static Object directRun(Class<?> initClazz, String funcName, Class[] paramTypes, Object[] args)
            throws ProjectException{
        String errorMsg = "Failed to invoke the function '%s' due to %s";
        Object response;
        try {
            // Create a stream to hold the output
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            PrintStream printStream = new PrintStream(byteArrayOutputStream);
            PrintStream existingStream = System.out;
            // Use defined stream
            System.setOut(printStream);
            final Method method = initClazz.getDeclaredMethod(funcName, paramTypes);
            response = method.invoke(null, args);
            System.out.flush();
            System.setOut(existingStream);
            if (response instanceof Throwable) {
                throw new ProjectException(String.format(errorMsg, funcName, response.toString()),
                        (Throwable) response);
            }
            return response;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new ProjectException(String.format(errorMsg, funcName, e.getMessage()), e);
        }
    }

    private BRunUtil() {
    }
}
