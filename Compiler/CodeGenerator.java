import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CodeGenerator {
    private SymbolTable symbolTable;
    private List<String> errors;

    public CodeGenerator(SymbolTable symbolTable, List<String> errors) {
        this.symbolTable = symbolTable;
        this.errors = errors;
    }

    public String generateCode(ASTNode ast) {
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

    private String inferReturnType(ASTNode expr) {
        if (expr == null) return "Double";
        if ("Literal".equals(expr.type)) {
            String value = expr.value;
            if (value.startsWith("\"") && value.endsWith("\"")) return "String";
            if (value.matches("[0-9]+(\\.[0-9]+)?")) return "Double";
            return "Unknown";
        }
        if ("Variable".equals(expr.type)) return symbolTable.getType(expr.value);
        if ("FunctionCall".equals(expr.type)) return symbolTable.getType(expr.value);
        if ("BinaryOp".equals(expr.type)) {
            String leftType = inferReturnType(expr.children.get(0));
            String rightType = inferReturnType(expr.children.get(1));
            if (leftType.equals("Double") && rightType.equals("Double")) return "Double";
            if (leftType.equals("String") || rightType.equals("String")) return "String";
            return "Double";
        }
        if ("Comparison".equals(expr.type)) return "Boolean";
        return "Double";
    }
}