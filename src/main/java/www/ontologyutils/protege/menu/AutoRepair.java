package www.ontologyutils.protege.menu;

import java.io.*;

import javax.swing.*;

import www.ontologyutils.protege.progress.ProgressWindow;
import www.ontologyutils.repair.OntologyRepair;
import www.ontologyutils.toolbox.Ontology;

public abstract class AutoRepair extends MutationAction {
    protected abstract OntologyRepair getOntologyRepair();

    protected boolean performMutation(Ontology ontology) {
        var progressWindow = new ProgressWindow(getEditorKit());
        var currentThread = Thread.currentThread();
        progressWindow.startProgress("Automatic Repair", () -> currentThread.interrupt());
        try {
            var repair = getOntologyRepair();
            repair.setInfoCallback(progressWindow::addMessage);
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
            progressWindow.stopProgress();
        }
    }
}
