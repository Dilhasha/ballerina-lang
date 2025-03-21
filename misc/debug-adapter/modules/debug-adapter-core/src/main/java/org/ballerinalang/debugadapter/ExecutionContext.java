/*
 * Copyright (c) 2019, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ballerinalang.debugadapter;

import com.sun.jdi.Location;
import com.sun.jdi.request.EventRequestManager;
import io.ballerina.projects.Project;
import org.ballerinalang.debugadapter.jdi.VirtualMachineProxyImpl;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Context holder for debug execution state related information.
 */
public class ExecutionContext {

    private IDebugProtocolClient client;
    private final JBallerinaDebugServer adapter;
    private VirtualMachineProxyImpl debuggeeVM;
    private DebugMode debugMode;
    private Project sourceProject;
    private String sourceProjectRoot;
    private final DebugProjectCache projectCache;
    private Process launchedProcess;
    private boolean terminateRequestReceived;
    private boolean supportsRunInTerminalRequest;
    private DebugInstruction prevInstruction;
    private Location prevLocation;

    ExecutionContext(JBallerinaDebugServer adapter) {
        this.adapter = adapter;
        this.projectCache = new DebugProjectCache();
        this.prevInstruction = DebugInstruction.CONTINUE;
        this.prevLocation = null;
    }

    public Optional<Process> getLaunchedProcess() {
        return Optional.ofNullable(launchedProcess);
    }

    public void setLaunchedProcess(Process launchedProcess) {
        this.launchedProcess = launchedProcess;
    }

    public IDebugProtocolClient getClient() {
        return client;
    }

    public void setClient(IDebugProtocolClient client) {
        this.client = client;
    }

    public JBallerinaDebugServer getAdapter() {
        return adapter;
    }

    public VirtualMachineProxyImpl getDebuggeeVM() {
        return debuggeeVM;
    }

    public DebugOutputLogger getOutputLogger() {
        return getAdapter().getOutputLogger();
    }

    public void setDebuggeeVM(VirtualMachineProxyImpl debuggeeVM) {
        this.debuggeeVM = debuggeeVM;
    }

    public EventRequestManager getEventManager() {
        if (debuggeeVM == null) {
            return null;
        }
        return debuggeeVM.eventRequestManager();
    }

    public BufferedReader getInputStream() {
        if (launchedProcess == null) {
            return null;
        }
        return new BufferedReader(new InputStreamReader(launchedProcess.getInputStream(), StandardCharsets.UTF_8));
    }

    public BufferedReader getErrorStream() {
        if (launchedProcess == null) {
            return null;
        }
        return new BufferedReader(new InputStreamReader(launchedProcess.getErrorStream(), StandardCharsets.UTF_8));
    }

    public DebugInstruction getPrevInstruction() {
        return prevInstruction;
    }

    public void setPrevInstruction(DebugInstruction prevInstruction) {
        this.prevInstruction = prevInstruction;
    }

    public Optional<Location> getPrevLocation() {
        return Optional.ofNullable(prevLocation);
    }

    public void setPrevLocation(Location prevLocation) {
        this.prevLocation = prevLocation;
    }

    public DebugMode getDebugMode() {
        return debugMode;
    }

    public void setDebugMode(DebugMode debugMode) {
        this.debugMode = debugMode;
    }

    public boolean isTerminateRequestReceived() {
        return terminateRequestReceived;
    }

    public void setTerminateRequestReceived(boolean terminationRequestReceived) {
        this.terminateRequestReceived = terminationRequestReceived;
    }

    public Project getSourceProject() {
        return sourceProject;
    }

    public void setSourceProject(Project sourceProject) {
        this.sourceProject = sourceProject;
        this.setSourceProjectRoot(sourceProject.sourceRoot().toAbsolutePath().toString());
    }

    public DebugProjectCache getProjectCache() {
        return projectCache;
    }

    public String getSourceProjectRoot() {
        return sourceProjectRoot;
    }

    public void setSourceProjectRoot(String sourceProjectRoot) {
        this.sourceProjectRoot = sourceProjectRoot;
    }

    public void setSupportsRunInTerminalRequest(boolean supportsRunInTerminalRequest) {
        this.supportsRunInTerminalRequest = supportsRunInTerminalRequest;
    }

    public boolean getSupportsRunInTerminalRequest() {
        return supportsRunInTerminalRequest;
    }

    public void reset() {
        this.projectCache.clear();
        this.debugMode = null;
        this.debuggeeVM = null;
        this.prevLocation = null;
        this.sourceProject = null;
        this.launchedProcess = null;
        this.sourceProjectRoot = null;
        this.terminateRequestReceived = false;
        this.supportsRunInTerminalRequest = false;
        this.prevInstruction = DebugInstruction.CONTINUE;
    }

    /**
     * Currently supported debug configuration modes.
     */
    public enum DebugMode {
        LAUNCH,
        ATTACH
    }
}
