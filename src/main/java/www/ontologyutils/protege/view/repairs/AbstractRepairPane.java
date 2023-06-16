package www.ontologyutils.protege.view.repairs;

import java.util.function.Predicate;

import javax.swing.*;

import www.ontologyutils.repair.OntologyRepair;
import www.ontologyutils.toolbox.Ontology;

public abstract class AbstractRepairPane extends JPanel {
    public abstract String getName();
    
    public abstract OntologyRepair getRepair(Predicate<Ontology> isRepaired);

    @Override
    public String toString() {
        return getName();
    }
}
