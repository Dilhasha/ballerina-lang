package io.ballerina.projects.configurations;

import io.ballerina.compiler.api.symbols.Qualifier;
import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.api.symbols.SymbolKind;
import io.ballerina.compiler.api.symbols.VariableSymbol;
import io.ballerina.compiler.syntax.tree.BasicLiteralNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.ModuleVariableDeclarationNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import io.ballerina.projects.Document;
import io.ballerina.projects.Module;
import io.ballerina.projects.ModuleId;
import io.ballerina.projects.Package;
import io.ballerina.projects.PackageCompilation;
import io.ballerina.projects.ProjectException;
import io.ballerina.projects.util.ProjectConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.ballerina.projects.directory.BuildProject.writeContent;
import static io.ballerina.projects.util.ProjectConstants.CONFIGURATION_TOML;

public class ConfigTomlBuilder {

    private static Map<Document, SyntaxTree> getSyntaxTreeMap(Module module) {
        Map<Document, SyntaxTree> syntaxTreeMap = new HashMap<>();
        module.documentIds().forEach(documentId -> {
            Document document = module.document(documentId);
            syntaxTreeMap.put(document, document.syntaxTree());
        });
        return syntaxTreeMap;
    }

    public static void build(PackageCompilation packageCompilation, Collection<ModuleId> moduleIds, Package packageInstance){
        //Analyze and find if configurables are there
        Map<Module, List<Symbol>> configDetails = new HashMap<>();
        for (ModuleId moduleId:moduleIds) {
            List<Symbol> filteredSymbols = packageCompilation.getSemanticModel(moduleId).moduleSymbols().stream()
                    .filter(symbol -> symbol.kind() == SymbolKind.VARIABLE
                            && ((VariableSymbol) symbol).qualifiers().contains(Qualifier.CONFIGURABLE))
                    .collect(Collectors.toList());
            if(!filteredSymbols.isEmpty()){
                configDetails.put(packageInstance.module(moduleId), filteredSymbols);
            }
        }

        Path configTomlFile =packageInstance.project().sourceRoot().
                resolve(ProjectConstants.TARGET_DIR_NAME).resolve(ProjectConstants.BIN_DIR_NAME).resolve(CONFIGURATION_TOML);
        String configContent = getConfigTomlContent(configDetails);
        if (!configDetails.isEmpty()) {
            // write content to Config.toml file
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

//    private static void writeContent(Path configTomlFile, String configContent) {
//
//    }

    private static String getConfigValue(Map<Document, SyntaxTree> syntaxTreeMap,
                                           VariableSymbol variableSymbol) {
        String configVal = variableSymbol.getName().get() + " = ";
        for (Map.Entry<Document, SyntaxTree> syntaxTreeEntry : syntaxTreeMap.entrySet()) {
            if (syntaxTreeEntry.getValue().containsModulePart()) {
                ModulePartNode modulePartNode = syntaxTreeMap.get(syntaxTreeEntry.getKey()).rootNode();
                for (Node node : modulePartNode.members()) {
                    if ((node.kind() == SyntaxKind.MODULE_VAR_DECL) &&
                            node instanceof ModuleVariableDeclarationNode) {
                        if(!variableSymbol.getLocation().isEmpty() &&
                                (variableSymbol.getLocation().get().lineRange().
                                        startLine().line() == node.location().lineRange().startLine().line())){
                            for (Node child : ((ModuleVariableDeclarationNode) node).children()) {
                                if(child instanceof BasicLiteralNode){
                                    configVal = configVal.concat(child.toString() + "\n");
                                }
                            }
                        }

                    }
                }
            }
        }
        return configVal;
    }

    private static String getConfigTomlContent(Map<Module, List<Symbol>> configDetails) {
        StringBuilder content = new StringBuilder();
        for (Module module: configDetails.keySet()){
            List<Symbol> configSymbols = configDetails.get(module);
            String moduleInfo = module.descriptor().org().value() + "." +
                    module.descriptor().name().toString();
            content.append("[" + moduleInfo + "]\n");
            for (Symbol symbol: configSymbols){
                if(symbol instanceof VariableSymbol){
                    VariableSymbol variableSymbol = (VariableSymbol) symbol;
                    Map<Document, SyntaxTree> syntaxTreeMap = getSyntaxTreeMap(module);
                    content.append(getConfigValue(syntaxTreeMap, variableSymbol));
                }
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
