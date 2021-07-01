package org.ballerinalang.testerina.plugins.utils;

import io.ballerina.compiler.syntax.tree.CaptureBindingPatternNode;
import io.ballerina.compiler.syntax.tree.ExpressionNode;
import io.ballerina.compiler.syntax.tree.ExpressionStatementNode;
import io.ballerina.compiler.syntax.tree.FunctionArgumentNode;
import io.ballerina.compiler.syntax.tree.FunctionBodyBlockNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.FunctionSignatureNode;
import io.ballerina.compiler.syntax.tree.ImportDeclarationNode;
import io.ballerina.compiler.syntax.tree.MapTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.MethodCallExpressionNode;
import io.ballerina.compiler.syntax.tree.ModuleMemberDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.NilTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeFactory;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.OptionalTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.ParenthesizedArgList;
import io.ballerina.compiler.syntax.tree.PositionalArgumentNode;
import io.ballerina.compiler.syntax.tree.QualifiedNameReferenceNode;
import io.ballerina.compiler.syntax.tree.ReturnTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.SeparatedNodeList;
import io.ballerina.compiler.syntax.tree.SimpleNameReferenceNode;
import io.ballerina.compiler.syntax.tree.StatementNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.Token;
import io.ballerina.compiler.syntax.tree.TypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.TypeParameterNode;
import io.ballerina.compiler.syntax.tree.TypedBindingPatternNode;
import io.ballerina.compiler.syntax.tree.VariableDeclarationNode;
import io.ballerina.compiler.syntax.tree.WildcardBindingPatternNode;
import io.ballerina.projects.Document;
import io.ballerina.projects.DocumentConfig;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.Module;
import io.ballerina.projects.ModuleId;
import io.ballerina.projects.Package;
import io.ballerina.projects.Project;
import io.ballerina.projects.plugins.AnalysisTask;
import io.ballerina.projects.plugins.CompilationAnalysisContext;

import java.lang.reflect.WildcardType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Analysis task used to register already defined test cases.
 *
 * @since 2.0.0
 */
public class TestRegistrationTask implements AnalysisTask<CompilationAnalysisContext> {

    public static final String TEST_EXEC_BAL = "test_executer.bal";

    @Override
    public void perform(CompilationAnalysisContext compilationAnalysisContext) {
        Package currentPackage = compilationAnalysisContext.currentPackage();
        Module module = currentPackage.getDefaultModule();
        DocumentConfig documentConfig = generateDocument(currentPackage.project(), module,
                 getTestFunctions(currentPackage), getInitFunctions(currentPackage));
        if (!isDocumentExistInModule(module, documentConfig)) {
            module.modify().addTestDocument(documentConfig).apply();
            currentPackage.getCompilation();
        }

    }

    public List<FunctionDefinitionNode> getTestFunctions(Package currentPackage) {
        List<FunctionDefinitionNode> testFunctions = new ArrayList<>();
        for (ModuleId moduleId : currentPackage.moduleIds()) {
            Module module = currentPackage.module(moduleId);
            for (DocumentId documentId : module.testDocumentIds()) {
                Document document = module.document(documentId);
                Node node = document.syntaxTree().rootNode();
                TestFunctionVisitor testFunctionVisitor = new TestFunctionVisitor();
                node.accept(testFunctionVisitor);
                testFunctions.addAll(testFunctionVisitor.getFunctions());
            }
        }
        return testFunctions;
    }

    public List<FunctionDefinitionNode> getInitFunctions(Package currentPackage) {
        List<FunctionDefinitionNode> testFunctions = new ArrayList<>();
        for (ModuleId moduleId : currentPackage.moduleIds()) {
            Module module = currentPackage.module(moduleId);
            for (DocumentId documentId : module.testDocumentIds()) {
                Document document = module.document(documentId);
                Node node = document.syntaxTree().rootNode();
                InitFunctionVisitor initFunctionVisitor = new InitFunctionVisitor();
                node.accept(initFunctionVisitor);
                testFunctions.addAll(initFunctionVisitor.getFunctions());
            }
        }
        return testFunctions;
    }

    private DocumentConfig generateDocument(Project project, Module module,
                                            List<FunctionDefinitionNode> testFunctions,
                                            List<FunctionDefinitionNode> initFunctions) {
        //Create modulePartNode
        ModulePartNode modulePartNode = createModulePartNode(testFunctions, initFunctions);
        String newFileContent = modulePartNode.toSourceCode();
        Path filePath = project.sourceRoot().resolve("tests").resolve(TEST_EXEC_BAL);
        DocumentId newDocumentId = DocumentId.create(filePath.toString(), module.moduleId());
        return DocumentConfig.from(newDocumentId, newFileContent, TEST_EXEC_BAL);
    }

    public static boolean isDocumentExistInModule(Module module, DocumentConfig document) {
        for (DocumentId documentId : module.testDocumentIds()) {
            Document doc = module.document(documentId);
            if (document.name().equals(doc.name())) {
                return true;
            }
        }
        return false;
    }

    private static ExpressionStatementNode invokeRegisterFunction(String testNameVal, String testFuncVal) {
        SimpleNameReferenceNode registerReferenceNode =
                NodeFactory.createSimpleNameReferenceNode(NodeFactory.createIdentifierToken("register"));
        SimpleNameReferenceNode registrarRef =
                NodeFactory.createSimpleNameReferenceNode(NodeFactory.createIdentifierToken("registrar"));
        PositionalArgumentNode testName =
                NodeFactory.createPositionalArgumentNode(NodeFactory
                        .createBasicLiteralNode(SyntaxKind.STRING_LITERAL, NodeFactory
                                .createLiteralValueToken(SyntaxKind.STRING_LITERAL_TOKEN,
                                        "\"" + testNameVal + "\"",
                                        NodeFactory.createEmptyMinutiaeList(),
                                        NodeFactory.createEmptyMinutiaeList())));
        PositionalArgumentNode testFunction = NodeFactory.createPositionalArgumentNode(
                NodeFactory.createSimpleNameReferenceNode(NodeFactory.createIdentifierToken(testFuncVal)));

        SeparatedNodeList<FunctionArgumentNode> separatedNodeList = getFunctionParamList(testName, testFunction);
        MethodCallExpressionNode registerCall = NodeFactory.createMethodCallExpressionNode(registrarRef,
                NodeFactory.createToken(SyntaxKind.DOT_TOKEN),
                registerReferenceNode, NodeFactory.createToken(SyntaxKind.OPEN_PAREN_TOKEN), separatedNodeList,
                NodeFactory.createToken(SyntaxKind.CLOSE_PAREN_TOKEN));

        ExpressionStatementNode expressionStatementNode =
                NodeFactory.createExpressionStatementNode(SyntaxKind.CALL_STATEMENT, registerCall,
                        NodeFactory.createToken(SyntaxKind.SEMICOLON_TOKEN));
        return expressionStatementNode;
    }

    private static StatementNode invokeTestInitFunction(String testNameVal, int i) {
        SimpleNameReferenceNode simpleNameReferenceNode =
                NodeFactory.createSimpleNameReferenceNode(NodeFactory.createIdentifierToken(testNameVal));
        SeparatedNodeList<FunctionArgumentNode> separatedNodeList = getFunctionParamList(new PositionalArgumentNode[0]);
        ExpressionNode expressionNode = NodeFactory.createFunctionCallExpressionNode(simpleNameReferenceNode,
                NodeFactory.createToken(SyntaxKind.OPEN_PAREN_TOKEN), separatedNodeList,
                NodeFactory.createToken(SyntaxKind.CLOSE_PAREN_TOKEN));

        NilTypeDescriptorNode nilTypeDescriptorNode =
                NodeFactory.createNilTypeDescriptorNode(NodeFactory.createToken(SyntaxKind.OPEN_PAREN_TOKEN),
                        NodeFactory.createToken(SyntaxKind.CLOSE_PAREN_TOKEN, NodeFactory.createEmptyMinutiaeList(),
                                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))));
        StatementNode functionCallStatement =
                getFunctionCallStatement("v" + i, nilTypeDescriptorNode, expressionNode, true);
        return functionCallStatement;
    }


    private static FunctionDefinitionNode createInitFunction() {
        // Function signature
        OptionalTypeDescriptorNode optionalErrorTypeDescriptorNode =
                NodeFactory.createOptionalTypeDescriptorNode(
                        NodeFactory.createParameterizedTypeDescriptorNode(
                                SyntaxKind.ERROR_TYPE_DESC, NodeFactory.createToken(SyntaxKind.ERROR_KEYWORD), null),
                        NodeFactory.createToken(SyntaxKind.QUESTION_MARK_TOKEN, NodeFactory.createEmptyMinutiaeList(),
                                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))));

        ReturnTypeDescriptorNode returnTypeDescriptorNode =
                NodeFactory.createReturnTypeDescriptorNode(NodeFactory
                                .createToken(SyntaxKind.RETURNS_KEYWORD, NodeFactory.createMinutiaeList(
                                        NodeFactory.createWhitespaceMinutiae(" ")),
                                        NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))),
                        NodeFactory.createEmptyNodeList(), optionalErrorTypeDescriptorNode);
        FunctionSignatureNode functionSignatureNode =
                NodeFactory.createFunctionSignatureNode(NodeFactory.createToken(SyntaxKind.OPEN_PAREN_TOKEN),
                        NodeFactory.createSeparatedNodeList(),
                        NodeFactory.createToken(SyntaxKind.CLOSE_PAREN_TOKEN), returnTypeDescriptorNode);

        //Function body
        SimpleNameReferenceNode simpleNameReferenceNode =
                NodeFactory.createSimpleNameReferenceNode(NodeFactory.createIdentifierToken("testExecute"));
        SeparatedNodeList<FunctionArgumentNode> separatedNodeList = getFunctionParamList(new PositionalArgumentNode[0]);
        ExpressionNode expressionNode = NodeFactory.createFunctionCallExpressionNode(simpleNameReferenceNode,
                NodeFactory.createToken(SyntaxKind.OPEN_PAREN_TOKEN), separatedNodeList,
                NodeFactory.createToken(SyntaxKind.CLOSE_PAREN_TOKEN));

        NilTypeDescriptorNode nilTypeDescriptorNode =
                NodeFactory.createNilTypeDescriptorNode(NodeFactory.createToken(SyntaxKind.OPEN_PAREN_TOKEN),
                        NodeFactory.createToken(SyntaxKind.CLOSE_PAREN_TOKEN, NodeFactory.createEmptyMinutiaeList(),
                                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))));
        StatementNode functionCallStatement =
                getFunctionCallStatement("v", nilTypeDescriptorNode, expressionNode, true);

        List<StatementNode> statements = new ArrayList<>();
        statements.add(functionCallStatement);

        FunctionBodyBlockNode functionBodyNode =
                NodeFactory.createFunctionBodyBlockNode(
                        NodeFactory.createToken(SyntaxKind.OPEN_BRACE_TOKEN, NodeFactory.createEmptyMinutiaeList(),
                                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae("\n"))),
                        null,
                        NodeFactory.createNodeList(statements.toArray(new StatementNode[0])),
                        NodeFactory.createToken(SyntaxKind.CLOSE_BRACE_TOKEN));
        return NodeFactory.createFunctionDefinitionNode(
                SyntaxKind.FUNCTION_DEFINITION, null,
                NodeFactory.createEmptyNodeList(),
                NodeFactory.createToken(SyntaxKind.FUNCTION_KEYWORD, NodeFactory.createEmptyMinutiaeList(),
                        NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))),
                NodeFactory.createIdentifierToken("init",
                        NodeFactory.createEmptyMinutiaeList(), NodeFactory.createMinutiaeList()),
                NodeFactory.createEmptyNodeList(), functionSignatureNode, functionBodyNode);
    }

    private static VariableDeclarationNode createClassVariableDeclaration(String packageName, String className,
                                                                          String varName, String constructName){
        QualifiedNameReferenceNode type =
                NodeFactory.createQualifiedNameReferenceNode(NodeFactory.createIdentifierToken(packageName),
                        NodeFactory.createToken(SyntaxKind.COLON_TOKEN),
                        NodeFactory.createIdentifierToken(className, NodeFactory.createEmptyMinutiaeList(),
                                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))));

        CaptureBindingPatternNode classVar =
                NodeFactory.createCaptureBindingPatternNode(
                        NodeFactory.createIdentifierToken(varName, NodeFactory.createEmptyMinutiaeList(),
                                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))));
        TypedBindingPatternNode typeBindingNode =
                NodeFactory.createTypedBindingPatternNode(type, classVar);
        ExpressionNode createRegistrarNode = NodeFactory.createCheckExpressionNode(SyntaxKind.CHECK_EXPRESSION,
                NodeFactory.createToken(SyntaxKind.CHECK_KEYWORD, NodeFactory.createEmptyMinutiaeList(),
                        NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))),
                createFunctionInvocationNode(constructName, new PositionalArgumentNode[0]));

        VariableDeclarationNode varInitialization = NodeFactory
                .createVariableDeclarationNode(NodeFactory.createEmptyNodeList(), null, typeBindingNode,
                        NodeFactory.createToken(SyntaxKind.EQUAL_TOKEN), createRegistrarNode,
                        NodeFactory.createToken(SyntaxKind.SEMICOLON_TOKEN, NodeFactory.createEmptyMinutiaeList(),
                                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae("\n"))));
        return varInitialization;
    }

    private static VariableDeclarationNode createNewObject(String packageName, String className,
                                                           String varName, ParenthesizedArgList args){
        QualifiedNameReferenceNode type =
                NodeFactory.createQualifiedNameReferenceNode(NodeFactory.createIdentifierToken(packageName),
                        NodeFactory.createToken(SyntaxKind.COLON_TOKEN),
                        NodeFactory.createIdentifierToken(className, NodeFactory.createEmptyMinutiaeList(),
                                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))));
        CaptureBindingPatternNode classVar =
                NodeFactory.createCaptureBindingPatternNode(
                        NodeFactory.createIdentifierToken(varName, NodeFactory.createEmptyMinutiaeList(),
                                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))));
        TypedBindingPatternNode typeBindingNode =
                NodeFactory.createTypedBindingPatternNode(type, classVar);
        ExpressionNode createNewNode = NodeFactory.createImplicitNewExpressionNode(
                NodeFactory.createToken(SyntaxKind.NEW_KEYWORD), args);
        VariableDeclarationNode varInitialization = NodeFactory
                .createVariableDeclarationNode(NodeFactory.createEmptyNodeList(), null, typeBindingNode,
                        NodeFactory.createToken(SyntaxKind.EQUAL_TOKEN), createNewNode,
                        NodeFactory.createToken(SyntaxKind.SEMICOLON_TOKEN, NodeFactory.createEmptyMinutiaeList(),
                                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae("\n"))));
        return varInitialization;
    }

    public static StatementNode getFunctionCallStatement(String varName, TypeDescriptorNode typeDescriptorNode,
                                                         ExpressionNode inv, boolean checked) {
        ExpressionNode expr;
        if (checked) {
            expr = NodeFactory.createCheckExpressionNode(SyntaxKind.CHECK_EXPRESSION,
                    NodeFactory.createToken(SyntaxKind.CHECK_KEYWORD, NodeFactory.createEmptyMinutiaeList(),
                            NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))), inv);
        } else {
            expr = inv;
        }
        CaptureBindingPatternNode captureBindingPatternNode =
                NodeFactory.createCaptureBindingPatternNode(NodeFactory.createIdentifierToken(varName,
                        NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" ")),
                        NodeFactory.createEmptyMinutiaeList()));
        TypedBindingPatternNode typedBindingPatternNode =
                NodeFactory.createTypedBindingPatternNode(typeDescriptorNode, captureBindingPatternNode);
        VariableDeclarationNode variableDeclarationNode = NodeFactory
                .createVariableDeclarationNode(NodeFactory.createEmptyNodeList(), null, typedBindingPatternNode,
                        NodeFactory.createToken(SyntaxKind.EQUAL_TOKEN), expr,
                        NodeFactory.createToken(SyntaxKind.SEMICOLON_TOKEN, NodeFactory.createEmptyMinutiaeList(),
                                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae("\n"))));
        return variableDeclarationNode;
    }

    private static FunctionSignatureNode getFunctionSignature() {
        OptionalTypeDescriptorNode optionalErrorTypeDescriptorNode =
                NodeFactory.createOptionalTypeDescriptorNode(
                        NodeFactory.createParameterizedTypeDescriptorNode(
                                SyntaxKind.ERROR_TYPE_DESC, NodeFactory.createToken(SyntaxKind.ERROR_KEYWORD), null),
                        NodeFactory.createToken(SyntaxKind.QUESTION_MARK_TOKEN, NodeFactory.createEmptyMinutiaeList(),
                                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))));

        ReturnTypeDescriptorNode returnTypeDescriptorNode =
                NodeFactory.createReturnTypeDescriptorNode(NodeFactory
                                .createToken(SyntaxKind.RETURNS_KEYWORD, NodeFactory.createMinutiaeList(
                                        NodeFactory.createWhitespaceMinutiae(" ")),
                                        NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))),
                        NodeFactory.createEmptyNodeList(), optionalErrorTypeDescriptorNode);

        FunctionSignatureNode functionSignatureNode =
                NodeFactory.createFunctionSignatureNode(NodeFactory.createToken(SyntaxKind.OPEN_PAREN_TOKEN),
                        NodeFactory.createSeparatedNodeList(),
                        NodeFactory.createToken(SyntaxKind.CLOSE_PAREN_TOKEN), returnTypeDescriptorNode);
        return functionSignatureNode;
    }

    private static FunctionDefinitionNode createTestExecuteFunction(List<FunctionDefinitionNode> testFunctions,
                                                                    List<FunctionDefinitionNode> initFunctions) {
        List<StatementNode> statements = new ArrayList<>();
        int i = 1;
        // Initialize test registration
        for (FunctionDefinitionNode func : initFunctions) {
            statements.add(invokeTestInitFunction(func.functionName().toString(), i));
            i++;
        }
        //Function body
        VariableDeclarationNode registrarInitialization = createClassVariableDeclaration("dtest",
                "Registrar", "registrar", "getRegistrar");
        // Register function calls
        statements.add(registrarInitialization);
        for (FunctionDefinitionNode func : testFunctions) {
            statements.add(invokeRegisterFunction(func.functionName().toString(), func.functionName().toString()));
        }

        // Append execution logic
        appendExecutionLogic(statements);

        FunctionBodyBlockNode functionBodyNode =
                NodeFactory.createFunctionBodyBlockNode(
                        NodeFactory.createToken(SyntaxKind.OPEN_BRACE_TOKEN, NodeFactory.createEmptyMinutiaeList(),
                                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae("\n"))),
                        null,
                        NodeFactory.createNodeList(statements.toArray(new StatementNode[0])),
                        NodeFactory.createToken(SyntaxKind.CLOSE_BRACE_TOKEN));
        return NodeFactory.createFunctionDefinitionNode(
                SyntaxKind.FUNCTION_DEFINITION, null,
                NodeFactory.createEmptyNodeList(),
                NodeFactory.createToken(SyntaxKind.FUNCTION_KEYWORD, NodeFactory.createEmptyMinutiaeList(),
                        NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))),
                NodeFactory.createIdentifierToken("testExecute",
                        NodeFactory.createEmptyMinutiaeList(), NodeFactory.createMinutiaeList()),
                NodeFactory.createEmptyNodeList(), getFunctionSignature(), functionBodyNode);
    }

    private static void appendExecutionLogic(List<StatementNode> statements) {
        statements.add(createNewObject("dtest", "Executer", "executer",
                NodeFactory.createParenthesizedArgList(NodeFactory.createToken(SyntaxKind.OPEN_PAREN_TOKEN),
                        NodeFactory.createSeparatedNodeList(), NodeFactory.createToken(SyntaxKind.CLOSE_PAREN_TOKEN))));
        //    map<future<error?>> testWorkers = check executer.execute();
        statements.add(createTestWorkers());

        PositionalArgumentNode testWorkers = NodeFactory.createPositionalArgumentNode(
                NodeFactory.createSimpleNameReferenceNode(NodeFactory.createIdentifierToken("testWorkers")));
        statements.add(createNewObject("dtest", "Reporter", "reporter",
                NodeFactory.createParenthesizedArgList(NodeFactory.createToken(SyntaxKind.OPEN_PAREN_TOKEN),
                        NodeFactory.createSeparatedNodeList(testWorkers),
                        NodeFactory.createToken(SyntaxKind.CLOSE_PAREN_TOKEN))));

        MethodCallExpressionNode reportPrintCall = NodeFactory.createMethodCallExpressionNode(
                NodeFactory.createSimpleNameReferenceNode(NodeFactory.createIdentifierToken("reporter")),
                NodeFactory.createToken(SyntaxKind.DOT_TOKEN),
                NodeFactory.createSimpleNameReferenceNode(NodeFactory.createIdentifierToken("print")),
                NodeFactory.createToken(SyntaxKind.OPEN_PAREN_TOKEN), NodeFactory.createSeparatedNodeList(),
                NodeFactory.createToken(SyntaxKind.CLOSE_PAREN_TOKEN));
        ExpressionStatementNode expressionStatementNode =
                NodeFactory.createExpressionStatementNode(SyntaxKind.CALL_STATEMENT, reportPrintCall,
                        NodeFactory.createToken(SyntaxKind.SEMICOLON_TOKEN));
        statements.add(expressionStatementNode);
    }

    private static VariableDeclarationNode createTestWorkers() {
        OptionalTypeDescriptorNode optionalErrorTypeDescriptorNode =
                NodeFactory.createOptionalTypeDescriptorNode(
                        NodeFactory.createParameterizedTypeDescriptorNode(
                                SyntaxKind.ERROR_TYPE_DESC, NodeFactory.createToken(SyntaxKind.ERROR_KEYWORD), null),
                        NodeFactory.createToken(SyntaxKind.QUESTION_MARK_TOKEN, NodeFactory.createEmptyMinutiaeList(),
                                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))));
        TypeParameterNode param = NodeFactory.createTypeParameterNode(NodeFactory.createToken(SyntaxKind.LT_TOKEN),
                optionalErrorTypeDescriptorNode, NodeFactory.createToken(SyntaxKind.GT_TOKEN));

        TypeParameterNode typeParameterNode = NodeFactory.createTypeParameterNode(NodeFactory.createToken(SyntaxKind.LT_TOKEN),
                NodeFactory.createParameterizedTypeDescriptorNode(null, NodeFactory.createToken(SyntaxKind.FUTURE_KEYWORD),
                        param), NodeFactory.createToken(SyntaxKind.GT_TOKEN));
        MapTypeDescriptorNode type = NodeFactory.createMapTypeDescriptorNode(NodeFactory.createToken(SyntaxKind.MAP_KEYWORD),
                        typeParameterNode);

        CaptureBindingPatternNode var = NodeFactory.createCaptureBindingPatternNode(
                        NodeFactory.createIdentifierToken("testWorkers", NodeFactory.createEmptyMinutiaeList(),
                                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))));
        TypedBindingPatternNode typeBindingNode =
                NodeFactory.createTypedBindingPatternNode(type, var);

        MethodCallExpressionNode executeCall = NodeFactory.createMethodCallExpressionNode(
                NodeFactory.createSimpleNameReferenceNode(NodeFactory.createIdentifierToken("executer")),
                NodeFactory.createToken(SyntaxKind.DOT_TOKEN),
                NodeFactory.createSimpleNameReferenceNode(NodeFactory.createIdentifierToken("execute")),
                NodeFactory.createToken(SyntaxKind.OPEN_PAREN_TOKEN), NodeFactory.createSeparatedNodeList(),
                NodeFactory.createToken(SyntaxKind.CLOSE_PAREN_TOKEN));

        ExpressionNode createWorkerNode = NodeFactory.createCheckExpressionNode(SyntaxKind.CHECK_EXPRESSION,
                NodeFactory.createToken(SyntaxKind.CHECK_KEYWORD, NodeFactory.createEmptyMinutiaeList(),
                        NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))), executeCall);

        VariableDeclarationNode varInitialization = NodeFactory
                .createVariableDeclarationNode(NodeFactory.createEmptyNodeList(), null, typeBindingNode,
                        NodeFactory.createToken(SyntaxKind.EQUAL_TOKEN), createWorkerNode,
                        NodeFactory.createToken(SyntaxKind.SEMICOLON_TOKEN, NodeFactory.createEmptyMinutiaeList(),
                                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae("\n"))));
        return varInitialization;
    }

    private static SeparatedNodeList<FunctionArgumentNode> getFunctionParamList(PositionalArgumentNode... args) {
        List<Node> nodeList = new ArrayList<>();
        for (PositionalArgumentNode arg : args) {
            nodeList.add(arg);
            nodeList.add(NodeFactory.createToken(SyntaxKind.COMMA_TOKEN));
        }
        if (args.length > 0) {
            nodeList.remove(nodeList.size() - 1);
        }
        return NodeFactory.createSeparatedNodeList(nodeList);
    }

    public static ExpressionNode createFunctionInvocationNode(String functionName, PositionalArgumentNode... args) {
        QualifiedNameReferenceNode simpleNameReferenceNode =
                NodeFactory.createQualifiedNameReferenceNode(NodeFactory.createIdentifierToken("dtest"),
                        NodeFactory.createToken(SyntaxKind.COLON_TOKEN),
                        NodeFactory.createIdentifierToken(functionName));
        SeparatedNodeList<FunctionArgumentNode> separatedNodeList = getFunctionParamList(args);
        return NodeFactory.createFunctionCallExpressionNode(simpleNameReferenceNode,
                NodeFactory.createToken(SyntaxKind.OPEN_PAREN_TOKEN), separatedNodeList,
                NodeFactory.createToken(SyntaxKind.CLOSE_PAREN_TOKEN));
    }

    private static ModulePartNode createModulePartNode(List<FunctionDefinitionNode> testFunctions,
                                                       List<FunctionDefinitionNode> initFunctions) {
        ImportDeclarationNode dtestImport =
                NodeFactory.createImportDeclarationNode(NodeFactory.createToken(SyntaxKind.IMPORT_KEYWORD,
                        NodeFactory.createEmptyMinutiaeList(),
                        NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae(" "))),
                        NodeFactory.createImportOrgNameNode(
                                NodeFactory.createIdentifierToken("ballerina_test_engine"),
                                NodeFactory.createToken(SyntaxKind.DOT_TOKEN)),
                        NodeFactory.createSeparatedNodeList(
                                NodeFactory.createIdentifierToken("dtest")), null,
                        NodeFactory.createToken(SyntaxKind.SEMICOLON_TOKEN, NodeFactory.createEmptyMinutiaeList(),
                                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae("\n"))));
        List<ModuleMemberDeclarationNode> memberDeclarationNodeList = new ArrayList<>();
        memberDeclarationNodeList.add(createInitFunction());
        memberDeclarationNodeList.add(createTestExecuteFunction(testFunctions, initFunctions));
        NodeList<ModuleMemberDeclarationNode> nodeList = NodeFactory.createNodeList(memberDeclarationNodeList);
        Token eofToken = NodeFactory.createToken(SyntaxKind.EOF_TOKEN, NodeFactory.createEmptyMinutiaeList(),
                NodeFactory.createMinutiaeList(NodeFactory.createWhitespaceMinutiae("\n")));
        return NodeFactory.createModulePartNode(NodeFactory.createNodeList(dtestImport), nodeList, eofToken);
    }


}

