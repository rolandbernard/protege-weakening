package www.ontologyutils.protege.progress;

import java.awt.*;

import javax.swing.*;

import org.protege.editor.core.editorkit.EditorKit;

public class ProgressWindow {
    private final EditorKit editorKit;
    private JProgressBar progressBar = new JProgressBar();
    private JTextArea messages = new JTextArea(10, 30);
    private JDialog window;

    public ProgressWindow(final EditorKit editorKit) {
        this.editorKit = editorKit;
    }

    private void initWindow() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        progressBar.setIndeterminate(true);
        panel.add(progressBar, BorderLayout.SOUTH);

        JScrollPane scrollPane = new JScrollPane(messages);
        messages.setEditable(false);
        messages.setLineWrap(false);
        messages.setBackground(Color.WHITE);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel holderPanel = new JPanel(new BorderLayout(5, 5));
        holderPanel.add(panel, BorderLayout.NORTH);
        holderPanel.add(scrollPane, BorderLayout.CENTER);

        holderPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        Frame parent = (Frame) (SwingUtilities.getAncestorOfClass(Frame.class, editorKit.getWorkspace()));
        window = new JDialog(parent, "Repairing the ontology", true);
        window.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        window.getContentPane().setLayout(new BorderLayout());
        window.getContentPane().add(holderPanel, BorderLayout.CENTER);

        window.pack();
        Dimension windowSize = window.getPreferredSize();
        window.setModal(false);
        window.setSize(600, windowSize.height);
        window.setResizable(false);
    }

    private void deinitWindow() {
        window.setVisible(false);
        window.dispose();
        window = null;
    }

    private static String getTimeStamp() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date());
    }

    public void startProgress() {
        SwingUtilities.invokeLater(() -> {
            if (window == null) {
                initWindow();
                window.setLocationRelativeTo(window.getOwner());
                window.setVisible(true);
            }
        });
    }

    public void addMessage(final String msg) {
        SwingUtilities.invokeLater(() -> {
            messages.append("[" + getTimeStamp() + "] " + msg + "\n");
        });
    }

    public void stopProgress() {
        SwingUtilities.invokeLater(() -> {
            if (window != null) {
                deinitWindow();
            }
        });
    }
}
