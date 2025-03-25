import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;

public class CompilerGUI extends JFrame {
    private JTextArea inputArea;
    private JTextArea outputArea;
    private JButton runButton;
    private JButton clearButton;
    private JLabel statusLabel;
    private Compiler compiler;
    private JToolBar toolBar;

    public CompilerGUI() {
        // Window setup
        setTitle("Simple Compiler IDE");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 700);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(600, 400));

        // Main panel with border layout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(new Color(240, 240, 240));

        // Toolbar
        toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBackground(new Color(220, 220, 220));

        // Run button
        runButton = createStyledButton("Run", new Color(0, 120, 215));
        runButton.setToolTipText("Compile and run the code");
        runButton.addActionListener(e -> compileAndDisplay());

        // Clear button
        clearButton = createStyledButton("Clear", new Color(255, 87, 34));
        clearButton.setToolTipText("Clear input and output");
        clearButton.addActionListener(e -> clearAreas());

        toolBar.add(runButton);
        toolBar.addSeparator();
        toolBar.add(clearButton);

        // Status label
        statusLabel = new JLabel("Ready");
        statusLabel.setForeground(new Color(0, 150, 0));
        toolBar.add(Box.createHorizontalGlue());
        toolBar.add(statusLabel);

        // Split pane for input and output
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerSize(8);
        splitPane.setBackground(new Color(200, 200, 200));

        // Input panel
        JPanel inputPanel = createStyledPanel("Input Code");
        inputArea = createStyledTextArea();
        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180)));
        inputPanel.add(inputScroll, BorderLayout.CENTER);

        // Output panel
        JPanel outputPanel = createStyledPanel("Output");
        outputArea = createStyledTextArea();
        outputArea.setEditable(false);
        JScrollPane outputScroll = new JScrollPane(outputArea);
        outputScroll.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180)));
        outputPanel.add(outputScroll, BorderLayout.CENTER);

        // Assemble split pane
        splitPane.setTopComponent(inputPanel);
        splitPane.setBottomComponent(outputPanel);

        // Add components to main panel
        mainPanel.add(toolBar, BorderLayout.NORTH);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        // Add main panel to frame
        add(mainPanel);

        // Add menu bar
        setJMenuBar(createMenuBar());
    }

    private JButton createStyledButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JPanel createStyledPanel(String title) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(150, 150, 150)),
            title,
            javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
            javax.swing.border.TitledBorder.DEFAULT_POSITION,
            new Font("Arial", Font.BOLD, 12)
        ));
        panel.setBackground(Color.WHITE);
        return panel;
    }

    private JTextArea createStyledTextArea() {
        JTextArea textArea = new JTextArea(15, 50);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        return textArea;
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.setMnemonic(KeyEvent.VK_X);
        exitItem.addActionListener(e -> System.exit(0));
        
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        
        return menuBar;
    }

    private void compileAndDisplay() {
        try {
            statusLabel.setText("Compiling...");
            statusLabel.setForeground(new Color(0, 120, 215));
            
            String inputCode = inputArea.getText();
            compiler = new Compiler(inputCode);
            String output = compiler.compile();
            
            outputArea.setText("Generated Java Code:\n\n" + output);
            
            if (!compiler.errors.isEmpty()) {
                outputArea.append("\n\nErrors:\n");
                for (String error : compiler.errors) {
                    outputArea.append(error + "\n");
                }
                statusLabel.setText("Compilation Failed");
                statusLabel.setForeground(Color.RED);
            } else {
                statusLabel.setText("Compilation Successful");
                statusLabel.setForeground(new Color(0, 150, 0));
            }
            
            Compiler.writeFile("CompilerOutput.java", output);
            Compiler.writeTokens("tokens.txt", compiler.tokens);
            
        } catch (IOException ex) {
            outputArea.setText("Error: " + ex.getMessage());
            statusLabel.setText("Error Occurred");
            statusLabel.setForeground(Color.RED);
        }
    }

    private void clearAreas() {
        inputArea.setText("");
        outputArea.setText("");
        statusLabel.setText("Ready");
        statusLabel.setForeground(new Color(0, 150, 0));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new CompilerGUI().setVisible(true);
        });
    }
}
