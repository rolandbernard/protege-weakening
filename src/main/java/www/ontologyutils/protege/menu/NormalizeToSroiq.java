package www.ontologyutils.protege.menu;

import javax.swing.*;

import www.ontologyutils.normalization.SroiqNormalization;
import www.ontologyutils.toolbox.Ontology;

public class NormalizeToSroiq extends MutationAction {
    private SroiqNormalization normalization = new SroiqNormalization();

    @Override
    protected void performMutation(Ontology ontology) {
        try {
            normalization.apply(ontology);
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(getOWLWorkspace(), "Ontology has been normalized");
            });
        } catch (final Exception e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(getOWLWorkspace(), e.getMessage(), "Normalization failed",
                        JOptionPane.ERROR_MESSAGE);
            });
        }
    }
}
