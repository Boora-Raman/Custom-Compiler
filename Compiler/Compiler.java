import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Compiler {
    private String input;

    public Compiler(String input) {
        this.input = input;
    }

    public String compile() {
        // Lexical Analysis
        Lexer lexer = new Lexer(input);
        List<Token> tokens = lexer.tokenize();
        List<String> lexerErrors = lexer.getErrors();

        // Syntax Analysis
        Parser parser = new Parser(tokens);
        ASTNode ast = parser.parse();
        List<String> parserErrors = parser.getErrors();

        // Semantic Analysis
        Analyzer analyzer = new Analyzer(tokens);
        analyzer.analyze(ast);
        List<String> analyzerErrors = analyzer.getErrors();

        // Combine all errors
        List<String> allErrors = new ArrayList<>();
        allErrors.addAll(lexerErrors);
        allErrors.addAll(parserErrors);
        allErrors.addAll(analyzerErrors);

        // Code Generation
        CodeGenerator codeGenerator = new CodeGenerator(analyzer.getSymbolTable(), allErrors);
        return codeGenerator.generateCode(ast);
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
            System.out.println("Compilation complete. Output written to " + outputFile + " and tokens.txt");
            System.out.println(javaCode);

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
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
}