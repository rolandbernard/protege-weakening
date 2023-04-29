package www.ontologyutils.protege.menu;

import javax.swing.*;

import www.ontologyutils.protege.progress.ProgressWindow;
import www.ontologyutils.repair.OntologyRepair;
import www.ontologyutils.toolbox.Ontology;

public abstract class AutoRepair extends MutationAction {
    protected abstract OntologyRepair getOntologyRepair();

    protected boolean performMutation(final Ontology ontology) {
        final var progressWindow = new ProgressWindow(getEditorKit());
        final var currentThread = Thread.currentThread();
        progressWindow.startProgress("Automatic Repair", () -> currentThread.interrupt());
        try {
            final var repair = getOntologyRepair();
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
        } catch (final Exception e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(getOWLWorkspace(), e.getMessage(), "Repair failed",
                        JOptionPane.ERROR_MESSAGE);
            });
            return false;
        } finally {
            progressWindow.stopProgress();
        }
    }
}
