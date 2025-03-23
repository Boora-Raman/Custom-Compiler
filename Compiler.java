import java.io.*;
import java.util.*;
import java.util.regex.*;

class Token {
    String type;
    String value;
    int line;
    int column;

    Token(String type, String value, int line, int column) {
        this.type = type;
        this.value = value;
        this.line = line;
        this.column = column;
    }

    @Override
    public String toString() {
        return "[" + type + ": " + value + "] at line " + line + ", column " + column;
    }
}

class ErrorHandler {
    void error(String message, int line, int column) {
        try (FileWriter errorWriter = new FileWriter("errors.txt", true)) {
            errorWriter.write("Error at line " + line + ", column " + column + ": " + message + "\n");
        } catch (IOException e) {
            System.err.println("Error writing to errors.txt: " + e.getMessage());
        }
    }
}

class SymbolTable {
    private Map<String, String> table = new HashMap<>();
    private Map<String, List<String>> functionParams = new HashMap<>();
    private Map<String, Integer> lineNumbers = new HashMap<>();
    private Map<String, Integer> columnNumbers = new HashMap<>();

    void add(String identifier, String type, int line, int column) {
        table.put(identifier, type);
        lineNumbers.put(identifier, line);
        columnNumbers.put(identifier, column);
        System.out.println("Added to symbol table: " + identifier + " (Type: " + type + ", Line: " + line + ", Column: " + column + ")");
    }

    void addFunctionParams(String funcName, List<String> paramTypes) {
        functionParams.put(funcName, paramTypes);
    }

    List<String> getFunctionParams(String funcName) {
        return functionParams.getOrDefault(funcName, Collections.emptyList());
    }

    boolean contains(String identifier) {
        return table.containsKey(identifier);
    }

    String getType(String identifier) {
        return table.getOrDefault(identifier, "Double");
    }

    int getLine(String identifier) {
        return lineNumbers.getOrDefault(identifier, 0);
    }

    int getColumn(String identifier) {
        return columnNumbers.getOrDefault(identifier, 0);
    }
}

class ASTNode {
    String type;
    List<ASTNode> children;
    String value;

    ASTNode(String type) {
        this.type = type;
        this.children = new ArrayList<>();
    }
}

public class Compiler {
    private static final String[] KEYWORDS = {"if", "else", "for", "return", "call"};
    private static final String SINGLE_OPERATORS = "+-*/%=(){}<>;,.";
    private static final String RELATIONAL_OPERATORS = "== != <= >= < >";
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[0-9]+(\\.[0-9]+)?");
    private static final Pattern STRING_PATTERN = Pattern.compile("\"[^\"]*\"");

    private String input;
    private List<Token> tokens;
    private int pos;
    private List<String> errors;
    private SymbolTable symbolTable;
    private ErrorHandler errorHandler;

    public Compiler(String input) {
        this.input = input;
        this.tokens = new ArrayList<>();
        this.pos = 0;
        this.errors = new ArrayList<>();
        this.symbolTable = new SymbolTable();
        this.errorHandler = new ErrorHandler();
    }

    private void tokenize() {
        int line = 1;
        int column;
        String[] lines = input.split("\n");

        for (String currentLine : lines) {
            int i = 0;
            column = 1;

            while (i < currentLine.length()) {
                char ch = currentLine.charAt(i);

                if (Character.isWhitespace(ch)) {
                    if (ch == '\n') line++;
                    else column++;
                    i++;
                    continue;
                }

                if (i + 1 < currentLine.length()) {
                    String twoChars = currentLine.substring(i, i + 2);
                    if (RELATIONAL_OPERATORS.contains(twoChars)) {
                        tokens.add(new Token("OPERATOR", twoChars, line, column));
                        i += 2;
                        column += 2;
                        continue;
                    }
                }

                if (Character.isLetter(ch) || ch == '_') {
                    Matcher matcher = IDENTIFIER_PATTERN.matcher(currentLine.substring(i));
                    if (matcher.find()) {
                        String word = matcher.group();
                        String type = Arrays.asList(KEYWORDS).contains(word) ? "KEYWORD" : "IDENTIFIER";
                        tokens.add(new Token(type, word, line, column));
                        i += word.length();
                        column += word.length();
                        continue;
                    }
                }

                if (Character.isDigit(ch)) {
                    Matcher matcher = NUMBER_PATTERN.matcher(currentLine.substring(i));
                    if (matcher.find()) {
                        String number = matcher.group();
                        tokens.add(new Token("NUMBER", number, line, column));
                        i += number.length();
                        column += number.length();
                        continue;
                    }
                }

                if (ch == '"') {
                    Matcher matcher = STRING_PATTERN.matcher(currentLine.substring(i));
                    if (matcher.find()) {
                        String str = matcher.group();
                        tokens.add(new Token("STRING", str, line, column));
                        i += str.length();
                        column += str.length();
                        continue;
                    } else {
                        errors.add("Error at line " + line + ", column " + column + ": Unterminated string literal");
                        errorHandler.error("Unterminated string literal", line, column);
                        break;
                    }
                }

                if (SINGLE_OPERATORS.indexOf(ch) != -1) {
                    tokens.add(new Token("OPERATOR", String.valueOf(ch), line, column));
                    i++;
                    column++;
                    continue;
                }

                errors.add("Error at line " + line + ", column " + column + ": Unexpected character: " + ch);
                errorHandler.error("Unexpected character: " + ch, line, column);
                i++;
                column++;
            }
            line++;
        }
    }

    private ASTNode parse() {
        tokenize();
        ASTNode program = new ASTNode("Program");
        while (pos < tokens.size()) {
            ASTNode element = parseProgramElement();
            if (element != null) {
                program.children.add(element);
            } else {
                pos++;
            }
        }
        return program;
    }

    private ASTNode parseProgramElement() {
        Token current = currentToken();
        if (current == null) return null;

        if ("IDENTIFIER".equals(current.type)) {
            Token next = peek(1);
            if (next != null && "(".equals(next.value)) {
                int lookahead = 2;
                while (lookahead < tokens.size() && !tokens.get(pos + lookahead).value.equals(")")) {
                    lookahead++;
                }
                if (lookahead + 1 < tokens.size() && tokens.get(pos + lookahead).value.equals(")") &&
                    tokens.get(pos + lookahead + 1).value.equals("{")) {
                    return parseFunctionDefinition();
                }
            }
            return parseAssignment();
        } else if ("KEYWORD".equals(current.type) && "call".equals(current.value)) {
            return parseFunctionCall();
        }
        errors.add("Error at line " + current.line + ", column " + current.column +
            ": Expected assignment, function definition, or 'call', found '" + current.value + "'");
        return null;
    }

    private ASTNode parseFunctionDefinition() {
        ASTNode func = new ASTNode("Function");
        Token name = consume("IDENTIFIER");
        func.value = name.value;

        consume("OPERATOR", "(");
        ASTNode params = parseParameterList();
        func.children.add(params);
        consume("OPERATOR", ")");

        consume("OPERATOR", "{");
        while (currentToken() != null && !"}".equals(currentToken().value)) {
            ASTNode stmt = parseStatement();
            if (stmt != null) func.children.add(stmt);
            else break;
        }
        consume("OPERATOR", "}");
        return func;
    }

    private ASTNode parseParameterList() {
        ASTNode params = new ASTNode("Parameters");
        Token current = currentToken();
        if (current != null && !")".equals(current.value)) {
            Token param = consume("IDENTIFIER");
            if (param != null) {
                ASTNode paramNode = new ASTNode("Parameter");
                paramNode.value = param.value;
                params.children.add(paramNode);
            }
            while (currentToken() != null && ",".equals(currentToken().value)) {
                consume("OPERATOR", ",");
                param = consume("IDENTIFIER");
                if (param != null) {
                    ASTNode paramNode = new ASTNode("Parameter");
                    paramNode.value = param.value;
                    params.children.add(paramNode);
                }
            }
        }
        return params;
    }

    private ASTNode parseStatement() {
        Token current = currentToken();
        if (current == null) return null;

        if ("KEYWORD".equals(current.type)) {
            if ("call".equals(current.value)) {
                return parseFunctionCall();
            } else if ("return".equals(current.value)) {
                ASTNode returnStmt = new ASTNode("Return");
                consume("KEYWORD", "return");
                ASTNode expr = parseExpression();
                if (expr != null) returnStmt.children.add(expr);
                consume("OPERATOR", ";");
                return returnStmt;
            } else if ("if".equals(current.value)) {
                return parseIfStatement();
            }
        } else if ("IDENTIFIER".equals(current.type)) {
            return parseAssignment();
        }
        errors.add("Error at line " + current.line + ", column " + current.column +
            ": Expected 'call', 'return', 'if', or assignment, found '" + current.value + "'");
        return null;
    }

    private ASTNode parseIfStatement() {
        Token ifToken = consume("KEYWORD", "if");
        if (ifToken == null) return null;

        consume("OPERATOR", "(");
        ASTNode condition = parseComparisonExpression();
        if (condition == null) {
            errors.add("Error at line " + ifToken.line + ", column " + ifToken.column +
                ": Expected condition in 'if' statement");
            return null;
        }
        consume("OPERATOR", ")");

        consume("OPERATOR", "{");
        ASTNode thenBlock = new ASTNode("ThenBlock");
        while (currentToken() != null && !"}".equals(currentToken().value)) {
            ASTNode stmt = parseStatement();
            if (stmt != null) thenBlock.children.add(stmt);
            else break;
        }
        consume("OPERATOR", "}");

        ASTNode ifNode = new ASTNode("If");
        ifNode.children.add(condition);
        ifNode.children.add(thenBlock);
        return ifNode;
    }

    private ASTNode parseAssignment() {
        Token idToken = consume("IDENTIFIER");
        if (idToken == null) return null;

        Token next = currentToken();
        if (next != null && "=".equals(next.value)) {
            ASTNode stmt = new ASTNode("Assignment");
            stmt.value = idToken.value;
            consume("OPERATOR", "=");
            ASTNode expr = parseExpression();
            if (expr == null) {
                errors.add("Error at line " + idToken.line + ", column " + (idToken.column + 1) +
                    ": Expected expression after '='");
                return null;
            }
            stmt.children.add(expr);
            Token semicolon = consume("OPERATOR", ";");
            if (semicolon == null) {
                errors.add("Error at line " + idToken.line + ", column " + (idToken.column + 1) +
                    ": Expected ';' after assignment");
                return null;
            }
            return stmt;
        }
        errors.add("Error at line " + idToken.line + ", column " + idToken.column +
            ": Expected '=' after identifier '" + idToken.value + "' in assignment");
        return null;
    }

    private ASTNode parseFunctionCall() {
        Token callToken = consume("KEYWORD", "call");
        if (callToken == null) return null;

        Token idToken = consume("IDENTIFIER");
        if (idToken == null) {
            errors.add("Error at line " + callToken.line + ", column " + callToken.column +
                ": Expected identifier after 'call'");
            return null;
        }
        ASTNode stmt = new ASTNode("FunctionCall");
        stmt.value = idToken.value;
        consume("OPERATOR", "(");
        ASTNode args = parseArgumentList();
        stmt.children.add(args);
        consume("OPERATOR", ")");
        consume("OPERATOR", ";");
        return stmt;
    }

    private ASTNode parseExpression() {
        return parseComparisonExpression();
    }

    private ASTNode parseComparisonExpression() {
        ASTNode left = parseAdditiveExpression();
        Token current = currentToken();
        if (current != null && RELATIONAL_OPERATORS.contains(current.value)) {
            String op = consume("OPERATOR").value;
            ASTNode right = parseAdditiveExpression();
            if (right != null) {
                ASTNode compNode = new ASTNode("Comparison");
                compNode.value = op;
                compNode.children.add(left);
                compNode.children.add(right);
                return compNode;
            }
        }
        return left;
    }

    private ASTNode parseAdditiveExpression() {
        ASTNode left = parseMultiplicativeExpression();
        while (currentToken() != null && ("+".equals(currentToken().value) || "-".equals(currentToken().value))) {
            String op = consume("OPERATOR").value;
            ASTNode right = parseMultiplicativeExpression();
            if (right != null) {
                ASTNode newNode = new ASTNode("BinaryOp");
                newNode.value = op;
                newNode.children.add(left);
                newNode.children.add(right);
                left = newNode;
            }
        }
        return left;
    }

    private ASTNode parseMultiplicativeExpression() {
        ASTNode left = parseFactor();
        while (currentToken() != null && ("*".equals(currentToken().value) || "/".equals(currentToken().value))) {
            String op = consume("OPERATOR").value;
            ASTNode right = parseFactor();
            if (right != null) {
                ASTNode newNode = new ASTNode("BinaryOp");
                newNode.value = op;
                newNode.children.add(left);
                newNode.children.add(right);
                left = newNode;
            }
        }
        return left;
    }

    private ASTNode parseFactor() {
        Token current = currentToken();
        if (current == null) return null;

        if ("NUMBER".equals(current.type)) {
            ASTNode node = new ASTNode("Literal");
            node.value = consume("NUMBER").value;
            return node;
        } else if ("STRING".equals(current.type)) {
            ASTNode node = new ASTNode("Literal");
            node.value = consume("STRING").value;
            return node;
        } else if ("KEYWORD".equals(current.type) && "call".equals(current.value)) {
            consume("KEYWORD", "call");
            Token idToken = consume("IDENTIFIER");
            if (idToken == null) {
                errors.add("Error at line " + current.line + ", column " + current.column +
                    ": Expected identifier after 'call'");
                return null;
            }
            ASTNode call = new ASTNode("FunctionCall");
            call.value = idToken.value;
            consume("OPERATOR", "(");
            call.children.add(parseArgumentList());
            consume("OPERATOR", ")");
            return call;
        } else if ("IDENTIFIER".equals(current.type)) {
            String id = consume("IDENTIFIER").value;
            ASTNode node = new ASTNode("Variable");
            node.value = id;
            return node;
        } else if ("(".equals(current.value)) {
            consume("OPERATOR", "(");
            ASTNode expr = parseExpression();
            consume("OPERATOR", ")");
            return expr;
        }
        errors.add("Error at line " + current.line + ", column " + current.column +
            ": Invalid expression factor '" + current.value + "'");
        return null;
    }

    private ASTNode parseArgumentList() {
        ASTNode args = new ASTNode("Arguments");
        Token current = currentToken();
        if (current != null && !")".equals(current.value)) {
            ASTNode expr = parseExpression();
            if (expr != null) args.children.add(expr);
            while (currentToken() != null && ",".equals(currentToken().value)) {
                consume("OPERATOR", ",");
                expr = parseExpression();
                if (expr != null) args.children.add(expr);
            }
        }
        return args;
    }

    private Token currentToken() {
        return pos < tokens.size() ? tokens.get(pos) : null;
    }

    private Token peek(int ahead) {
        return pos + ahead < tokens.size() ? tokens.get(pos + ahead) : null;
    }

    private Token consume(String expectedType) {
        Token current = currentToken();
        if (current != null && expectedType.equals(current.type)) {
            pos++;
            return current;
        }
        if (current != null) {
            errors.add("Error at line " + current.line + ", column " + current.column +
                ": Expected " + expectedType + " but found " + current.type + "('" + current.value + "')");
        }
        return null;
    }

    private Token consume(String expectedType, String expectedValue) {
        Token current = currentToken();
        if (current != null && expectedType.equals(current.type) && expectedValue.equals(current.value)) {
            pos++;
            return current;
        }
        if (current != null) {
            errors.add("Error at line " + current.line + ", column " + current.column +
                ": Expected " + expectedValue + " but found " + current.value);
        }
        return null;
    }

    private void analyze(ASTNode ast) {
        for (ASTNode node : ast.children) {
            if ("Function".equals(node.type)) {
                analyzeFunction(node);
            } else if ("Assignment".equals(node.type)) {
                analyzeAssignment(node);
            } else if ("FunctionCall".equals(node.type)) {
                analyzeFunctionCall(node);
            }
            analyzeExpressions(node);
        }
    }

    private void analyzeExpressions(ASTNode node) {
        for (ASTNode child : node.children) {
            if ("FunctionCall".equals(child.type)) {
                Token idToken = tokens.stream()
                    .filter(t -> t.value.equals(child.value) && "IDENTIFIER".equals(t.type))
                    .findFirst()
                    .orElse(null);
                int line = idToken != null ? idToken.line : 
                           tokens.stream().filter(t -> t.value.equals("call") && t.line <= pos).mapToInt(t -> t.line).max().orElse(0);
                int column = idToken != null ? idToken.column : 
                             tokens.stream().filter(t -> t.value.equals("call") && t.line <= pos).mapToInt(t -> t.column).max().orElse(0);
                analyzeFunctionCall(child, line, column);
            }
            analyzeExpressions(child);
        }
    }

    private void analyzeFunction(ASTNode func) {
        String funcName = func.value;
        Token nameToken = tokens.stream().filter(t -> t.value.equals(funcName) && "IDENTIFIER".equals(t.type)).findFirst().orElse(null);
        int line = nameToken != null ? nameToken.line : 0;
        int column = nameToken != null ? nameToken.column : 0;
        symbolTable.add(funcName, "Function", line, column);

        ASTNode params = func.children.get(0);
        List<String> paramTypes = new ArrayList<>();
        for (ASTNode param : params.children) {
            symbolTable.add(param.value, "Double", line, column + funcName.length() + 2);
            paramTypes.add("Double");
        }
        symbolTable.addFunctionParams(funcName, paramTypes);

        String returnType = "Double";
        for (int i = 1; i < func.children.size(); i++) {
            ASTNode stmt = func.children.get(i);
            if ("Return".equals(stmt.type) && !stmt.children.isEmpty()) {
                returnType = inferReturnType(stmt.children.get(0));
                break;
            }
        }
        symbolTable.add(funcName, returnType, line, column);
    }

    private void analyzeAssignment(ASTNode assignment) {
        String varName = assignment.value;
        Token idToken = tokens.stream().filter(t -> t.value.equals(varName) && "IDENTIFIER".equals(t.type)).findFirst().orElse(null);
        int line = idToken != null ? idToken.line : 0;
        int column = idToken != null ? idToken.column : 0;
        ASTNode expr = assignment.children.get(0);
        String type = inferReturnType(expr);
        symbolTable.add(varName, type, line, column);

        if ("FunctionCall".equals(expr.type)) {
            analyzeFunctionCall(expr, line, column);
        }
    }

    private void analyzeFunctionCall(ASTNode call) {
        Token idToken = tokens.stream().filter(t -> t.value.equals(call.value) && "IDENTIFIER".equals(t.type)).findFirst().orElse(null);
        int line = idToken != null ? idToken.line : 0;
        int column = idToken != null ? idToken.column : 0;
        analyzeFunctionCall(call, line, column);
    }

    private void analyzeFunctionCall(ASTNode call, int line, int column) {
        String funcName = call.value;

        if (!symbolTable.contains(funcName) && !"print".equals(funcName)) {
            errors.add("Error at line " + line + ", column " + column +
                ": Undefined function '" + funcName + "'");
            return;
        }

        if (!"print".equals(funcName)) {
            List<String> expectedParamTypes = symbolTable.getFunctionParams(funcName);
            ASTNode args = call.children.get(0);
            if (expectedParamTypes.size() != args.children.size()) {
                errors.add("Error at line " + line + ", column " + column +
                    ": Incorrect number of arguments for function '" + funcName +
                    "'. Expected " + expectedParamTypes.size() + ", got " + args.children.size());
            } else {
                for (int i = 0; i < args.children.size(); i++) {
                    String expectedType = expectedParamTypes.get(i);
                    String actualType = inferReturnType(args.children.get(i));
                    if (!expectedType.equals(actualType)) {
                        errors.add("Error at line " + line + ", column " + column +
                            ": Type mismatch in argument " + (i + 1) + " of function '" + funcName +
                            "'. Expected " + expectedType + ", got " + actualType);
                    }
                }
            }
        }
    }

    private String inferReturnType(ASTNode expr) {
        if (expr == null) return "Double";
        if ("Literal".equals(expr.type)) {
            String value = expr.value;
            if (value.startsWith("\"") && value.endsWith("\"")) return "String";
            if (NUMBER_PATTERN.matcher(value).matches()) return "Double";
            return "Unknown";
        }
        if ("Variable".equals(expr.type)) return symbolTable.getType(expr.value);
        if ("FunctionCall".equals(expr.type)) return symbolTable.getType(expr.value);
        if ("BinaryOp".equals(expr.type)) {
            String leftType = inferReturnType(expr.children.get(0));
            String rightType = inferReturnType(expr.children.get(1));
            if (leftType.equals("Double") && rightType.equals("Double")) return "Double";
            if (leftType.equals("String") || rightType.equals("String")) return "String";
            errors.add("Type error in binary operation at line " +
                (tokens.size() > pos ? tokens.get(pos).line : 0) + ", column " +
                (tokens.size() > pos ? tokens.get(pos).column : 0) +
                ": incompatible types " + leftType + " and " + rightType);
            return "Double";
        }
        if ("Comparison".equals(expr.type)) return "Boolean";
        return "Double";
    }

    private String generateCode(ASTNode ast) {
        StringBuilder output = new StringBuilder();
        output.append("public class CompilerOutput {\n");

        Map<String, ASTNode> functions = new HashMap<>();
        for (ASTNode element : ast.children) {
            if ("Function".equals(element.type)) {
                functions.put(element.value, element);
            }
        }
        for (ASTNode func : functions.values()) {
            String returnType = symbolTable.getType(func.value);
            output.append("    public static ").append(returnType).append(" ")
                .append(func.value).append("(");

            ASTNode params = func.children.get(0);
            List<String> paramTypes = symbolTable.getFunctionParams(func.value);
            for (int i = 0; i < params.children.size(); i++) {
                if (i > 0) output.append(", ");
                output.append(paramTypes.get(i)).append(" ").append(params.children.get(i).value);
            }
            output.append(") {\n");

            for (int i = 1; i < func.children.size(); i++) {
                output.append("        ").append(generateStatement(func.children.get(i)));
            }
            output.append("    }\n\n");
        }

        output.append("    public static void main(String[] args) {\n");

        Map<String, String> variablesToDeclare = new HashMap<>();
        for (ASTNode element : ast.children) {
            if ("Assignment".equals(element.type)) {
                String varName = element.value;
                String type = inferReturnType(element.children.get(0));
                variablesToDeclare.put(varName, type);
            }
        }

        for (Map.Entry<String, String> entry : variablesToDeclare.entrySet()) {
            output.append("        ").append(entry.getValue()).append(" ").append(entry.getKey()).append(";\n");
        }

        for (ASTNode element : ast.children) {
            if ("Assignment".equals(element.type)) {
                String varName = element.value;
                String value = generateExpression(element.children.get(0));
                if (inferReturnType(element.children.get(0)).equals("Double") &&
                    element.children.get(0).type.equals("Literal") &&
                    !value.contains(".") && !value.startsWith("\"")) {
                    value += ".0";
                }
                output.append("        ").append(varName).append(" = ").append(value).append(";\n");
            } else if ("FunctionCall".equals(element.type)) {
                if ("print".equals(element.value)) {
                    if (element.children.get(0).children.isEmpty()) {
                        output.append("        System.out.println();\n");
                    } else {
                        String arg = generateExpression(element.children.get(0).children.get(0));
                        output.append("        System.out.println(").append(arg).append(");\n");
                    }
                }
            }
        }

        output.append("    }\n");
        output.append("}\n");

        if (!errors.isEmpty()) {
            output.append("\n/* Compilation Errors:\n");
            for (String error : errors) {
                output.append(error).append("\n");
            }
            output.append("*/");
        }

        return output.toString();
    }

    private String generateStatement(ASTNode stmt) {
        if ("Return".equals(stmt.type)) {
            return "return " + generateExpression(stmt.children.get(0)) + ";\n";
        } else if ("If".equals(stmt.type)) {
            StringBuilder sb = new StringBuilder();
            sb.append("if (").append(generateExpression(stmt.children.get(0))).append(") {\n");
            ASTNode thenBlock = stmt.children.get(1);
            for (ASTNode thenStmt : thenBlock.children) {
                sb.append("            ").append(generateStatement(thenStmt));
            }
            sb.append("        }\n");
            return sb.toString();
        }
        return "";
    }

    private String generateExpression(ASTNode expr) {
        if ("Literal".equals(expr.type)) {
            return expr.value;
        }
        if ("Variable".equals(expr.type)) return expr.value;
        if ("BinaryOp".equals(expr.type)) {
            return generateExpression(expr.children.get(0)) + " " + expr.value + " " +
                generateExpression(expr.children.get(1));
        }
        if ("Comparison".equals(expr.type)) {
            return generateExpression(expr.children.get(0)) + " " + expr.value + " " +
                generateExpression(expr.children.get(1));
        }
        if ("FunctionCall".equals(expr.type)) {
            return expr.value + "(" + generateArguments(expr.children.get(0)) + ")";
        }
        return "";
    }

    private String generateArguments(ASTNode args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.children.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(generateExpression(args.children.get(i)));
        }
        return sb.toString();
    }

    public String compile() {
        ASTNode ast = parse();
        analyze(ast);
        return generateCode(ast);
    }

    private static String readFile(String filename) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    private static void writeFile(String filename, String content) throws IOException {
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write(content);
        }
    }

    private static void writeTokens(String filename, List<Token> tokens) throws IOException {
        try (FileWriter writer = new FileWriter(filename)) {
            for (Token token : tokens) {
                writer.write(token.toString() + "\n");
            }
        }
    }

    public static void main(String[] args) {
        try {
            String inputFile = "input.txt";
            String outputFile = "CompilerOutput.java";
            if (args.length > 0) inputFile = args[0];
            if (args.length > 1) outputFile = args[1];

            String code = readFile(inputFile);
            Compiler compiler = new Compiler(code);
            String javaCode = compiler.compile();

            writeFile(outputFile, javaCode);
            writeTokens("tokens.txt", compiler.tokens);
            System.out.println("Compilation complete. Output written to " + outputFile + " and tokens.txt");
            System.out.println(javaCode);

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
