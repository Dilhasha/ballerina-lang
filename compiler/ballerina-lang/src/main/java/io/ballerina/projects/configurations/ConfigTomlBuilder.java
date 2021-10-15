package io.ballerina.projects.configurations;

import io.ballerina.projects.ProjectException;
import io.ballerina.projects.util.ProjectConstants;
import io.ballerina.runtime.internal.TypeChecker;
import org.wso2.ballerinalang.compiler.bir.model.BIRNode;
import org.wso2.ballerinalang.compiler.semantics.model.Scope;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BVarSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.SymTag;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.Symbols;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.util.Flags;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.ballerina.projects.directory.BuildProject.writeContent;
import static io.ballerina.projects.util.ProjectConstants.CONFIGURATION_TOML;

public class ConfigTomlBuilder {
    public static void dumpConfigToml(Path sourceRoot, String moduleName, BLangPackage bLangPackage){
        Path configTomlFile =sourceRoot.
                resolve(ProjectConstants.TARGET_DIR_NAME).resolve(ProjectConstants.BIN_DIR_NAME).
                resolve(CONFIGURATION_TOML);
        String configContent = getConfigTomlContent(moduleName, bLangPackage);
        if (!configContent.isEmpty()) {
            createIfNotExists(configTomlFile);
            writeContent(configTomlFile, configContent);
        } else {
            // when there are no configs to write
            // if Config.toml does not exists ---> Config.toml will not be created
            // if Config.toml exists          ---> content will be written to existing Config.toml
//            if (configTomlFile.toFile().exists()) {
//                writeContent(configTomlFile, configContent);
//            }
        }
    }

    private static String getConfigTomlContent(String moduleName,
            BLangPackage bLangPackage) {
        BIRNode.BIRPackage birPackage = bLangPackage.symbol.bir;
        //bLangPackage.getImports().get(0).symbol.bir.globalVars
        StringBuilder content = new StringBuilder();
        boolean moduleInfoAdded = false;
        for (BIRNode.BIRGlobalVariableDcl globalVar : birPackage.globalVars) {
            long globalVarFlags = globalVar.flags;
            if (Symbols.isFlagOn(globalVarFlags, Flags.CONFIGURABLE)) {
                if (!moduleInfoAdded) {
                    content.append("[" + moduleName + "]\n");
                    moduleInfoAdded = true;
                }
                if (!Symbols.isFlagOn(globalVarFlags, Flags.REQUIRED)) {
                    content.append("# ");
                }
                content.append(globalVar.name.value + " = ");
                content.append(" \n");
            }
        }
        //bLangPackage.getImports().get(0).symbol.bir.globalVars - Not working
        for (Scope.ScopeEntry entry : bLangPackage.getImports().get(0).symbol.scope.entries.values()) {
            BSymbol symbol = entry.symbol;

            //Getting annotations. --> symbol.annots
            if (symbol != null && symbol.tag == SymTag.VARIABLE && Symbols.isFlagOn(symbol.flags, Flags.CONFIGURABLE)) {
                System.out.println("get import symbols");
                System.out.println(((BVarSymbol) symbol).name.value);
            }
        }
        return String.valueOf(content);
    }

    private static void createIfNotExists(Path filePath) {
        try {
            Files.createDirectories(filePath.getParent());
        } catch (IOException ioException) {
            throw new ProjectException("Failed to create 'target/bin' to add Config.toml");
        }
        if (!filePath.toFile().exists()) {
            try {
                Files.createFile(filePath);
            } catch (IOException e) {
                throw new ProjectException("Failed to create 'target/bin/Config.toml' to add configurations");
            }
        }
    }
}
