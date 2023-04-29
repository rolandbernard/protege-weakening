package www.ontologyutils.protege.menu;

import javax.swing.JOptionPane;

import www.ontologyutils.normalization.SroiqNormalization;
import www.ontologyutils.toolbox.Ontology;

public class NormalizeToSroiq extends MutationAction {
    private SroiqNormalization normalization = new SroiqNormalization();

    @Override
    protected void performMutation(Ontology ontology) {
        normalization.apply(ontology);
        JOptionPane.showMessageDialog(getOWLWorkspace(), "Ontology has been normalized");
    }
}
