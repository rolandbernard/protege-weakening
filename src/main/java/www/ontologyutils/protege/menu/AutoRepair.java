package www.ontologyutils.protege.menu;

import javax.swing.*;

import www.ontologyutils.protege.progress.ProgressWindow;
import www.ontologyutils.repair.OntologyRepair;
import www.ontologyutils.toolbox.Ontology;

public abstract class AutoRepair extends MutationAction {
    protected abstract OntologyRepair getOntologyRepair();

    protected void performMutation(final Ontology ontology) {
        final var progressWindow = new ProgressWindow(getEditorKit());
        progressWindow.startProgress();
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
        } catch (final Exception e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(getOWLWorkspace(), e.getMessage(), "Repair failed",
                        JOptionPane.ERROR_MESSAGE);
            });
        } finally {
            progressWindow.stopProgress();
        }
    }
}
