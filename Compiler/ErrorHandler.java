import java.io.FileWriter;
import java.io.IOException;

public class ErrorHandler {
    void error(String message, int line, int column) {
        try (FileWriter errorWriter = new FileWriter("errors.txt", true)) {
            errorWriter.write("Error at line " + line + ", column " + column + ": " + message + "\n");
        } catch (IOException e) {
            System.err.println("Error writing to errors.txt: " + e.getMessage());
        }
    }
}