import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Analyzer {
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[0-9]+(\\.[0-9]+)?");
    private List<Token> tokens;
    private SymbolTable symbolTable;
    private List<String> errors;

    public Analyzer(List<Token> tokens) {
        this.tokens = tokens;
        this.symbolTable = new SymbolTable();
        this.errors = new ArrayList<>();
    }

    public void analyze(ASTNode ast) {
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
                           tokens.stream().filter(t -> t.value.equals("call")).mapToInt(t -> t.line).max().orElse(0);
                int column = idToken != null ? idToken.column : 
                             tokens.stream().filter(t -> t.value.equals("call")).mapToInt(t -> t.column).max().orElse(0);
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
            errors.add("Type error in binary operation: incompatible types " + leftType + " and " + rightType);
            return "Double";
        }
        if ("Comparison".equals(expr.type)) return "Boolean";
        return "Double";
    }

    public SymbolTable getSymbolTable() {
        return symbolTable;
    }

    public List<String> getErrors() {
        return errors;
    }
}