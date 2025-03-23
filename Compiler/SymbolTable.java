import java.util.*;

public class SymbolTable {
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