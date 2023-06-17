package www.ontologyutils.protege.view;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.Map;
import java.util.function.Predicate;

import org.protege.editor.owl.ui.view.AbstractOWLViewComponent;

import www.ontologyutils.protege.view.repairs.*;
import www.ontologyutils.toolbox.CanceledException;
import www.ontologyutils.toolbox.Ontology;

public class AutomaticRepairView extends AbstractOWLViewComponent {
    private JProgressBar progressBar = new JProgressBar();
    private JTextArea messages = new JTextArea(10, 30);
    private JButton cancelButton = new JButton("Cancel");
    private AbstractRepairPane currentRepair;
    private Predicate<Ontology> currentGoal;

    @Override
    protected void initialiseOWLView() throws Exception {
        setLayout(new BorderLayout(5, 5));

        var top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        var choice = new JPanel(new WrapLayout(FlowLayout.LEFT));
        var algoPanel = new JPanel();
        algoPanel.add(new JLabel("Repair using"));
        var algo = new JComboBox<AbstractRepairPane>();
        algo.addItem(new McsRepairPane());
        algo.addItem(new RemovalRepairPane());
        algo.addItem(new WeakeningRepairPane());
        algo.setSelectedIndex(0);
        currentRepair = algo.getItemAt(algo.getSelectedIndex());
        algo.addActionListener(e -> {
            currentRepair = algo.getItemAt(algo.getSelectedIndex());
            top.remove(1);
            top.add(currentRepair, 1);
            revalidate();
            repaint();
        });
        algoPanel.add(algo);
        choice.add(algoPanel);

        var goalPanel = new JPanel();
        goalPanel.add(new JLabel("Make ontology"));
        var goal = new JComboBox<String>();
        goal.addItem("Consistent");
        goal.addItem("Coherent");
        goal.setSelectedIndex(0);
        var goals = Map.<String, Predicate<Ontology>>of("Consistent", Ontology::isConsistent, "Coherent",
                Ontology::isCoherent);
        currentGoal = goals.get(goal.getItemAt(goal.getSelectedIndex()));
        goal.addActionListener(e -> { 
            currentGoal = goals.get(goal.getItemAt(goal.getSelectedIndex()));
        });
        goalPanel.add(goal);
        choice.add(goalPanel);
        top.add(choice, 0);
        top.add(currentRepair, 1);
        add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(5, 5));
        progressBar.setIndeterminate(false);
        progressBar.setValue(0);
        progressBar.setMaximum(1);
        center.add(progressBar, BorderLayout.NORTH);
        JScrollPane scrollPane = new JScrollPane(messages);
        messages.setEditable(false);
        messages.setLineWrap(false);
        messages.setBackground(Color.WHITE);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        center.add(scrollPane, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        var bottom = new JPanel(new GridLayout(1, 2, 5, 0));
        var repairButton = new JButton("Repair");
        repairButton.addActionListener(e -> {
            repairActiveOntology();
        });
        cancelButton.setEnabled(false);
        bottom.add(cancelButton, 0);
        bottom.add(repairButton, 1);
        add(bottom, BorderLayout.SOUTH);
    }

    @Override
    protected void disposeOWLView() {
    }

    private static String getTimeStamp() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date());
    }

    private void startProgress(String name, Runnable onCancel) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setIndeterminate(true);
            progressBar.setValue(0);
            cancelButton.setEnabled(onCancel != null);
            for (var listener : cancelButton.getActionListeners()) {
                cancelButton.removeActionListener(listener);
            }
            if (onCancel != null) {
                cancelButton.addActionListener(e -> onCancel.run());
            }
        });
    }

    private void addMessage(String msg) {
        SwingUtilities.invokeLater(() -> {
            messages.append("[" + getTimeStamp() + "] " + msg + "\n");
            messages.scrollRectToVisible(new Rectangle(1, messages.getHeight(), 1, 1));
        });
    }

    private void stopProgress() {
        SwingUtilities.invokeLater(() -> {
            progressBar.setIndeterminate(false);
            progressBar.setValue(1);
            cancelButton.setEnabled(false);
        });
    }

    private boolean performRepair(Ontology ontology) {
        var currentThread = Thread.currentThread();
        startProgress("Automatic Repair", () -> currentThread.interrupt());
        try {
            var repair = currentRepair.getRepair(currentGoal);
            repair.setInfoCallback(this::addMessage);
            if (repair.isRepaired(ontology)) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(getOWLWorkspace(), "Ontology is already repaired");
                });
            } else {
                repair.apply(ontology);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(getOWLWorkspace(), "Ontology has been repaired");
                });
            }
            return true;
        } catch (CanceledException e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(getOWLWorkspace(), e.getMessage(), "Repair canceled",
                        JOptionPane.WARNING_MESSAGE);
            });
            return false;
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(getOWLWorkspace(), sw.toString(), "Repair failed",
                        JOptionPane.ERROR_MESSAGE);
            });
            return false;
        } finally {
            stopProgress();
        }
    }

    private void repairActiveOntology() {
        var thread = new Thread(() -> {
            var reasonerFactory = getOWLModelManager().getOWLReasonerManager()
                    .getCurrentReasonerFactory().getReasonerFactory();
            var owlOntology = getOWLModelManager().getActiveOntology();
            var ontology = Ontology.withAxiomsFrom(owlOntology, reasonerFactory);
            if (performRepair(ontology)) {
                SwingUtilities.invokeLater(() -> {
                    ontology.applyChangesTo(owlOntology);
                    ontology.close();
                });
            }
        });
        thread.start();
    }
}
