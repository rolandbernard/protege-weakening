package www.ontologyutils.protege.menu;

import javax.swing.JOptionPane;

import www.ontologyutils.repair.OntologyRepair;
import www.ontologyutils.toolbox.Ontology;

public abstract class AutoRepair extends MutationAction {
    protected abstract OntologyRepair getOntologyRepair();

    protected void performMutation(final Ontology ontology) {
        final var repair = getOntologyRepair();
        if (repair.isRepaired(ontology)) {
            JOptionPane.showMessageDialog(getOWLWorkspace(), "Ontology is already repaired");
        } else {
            repair.apply(ontology);
            JOptionPane.showMessageDialog(getOWLWorkspace(), "Ontology has been repaired");
        }
    }
}
