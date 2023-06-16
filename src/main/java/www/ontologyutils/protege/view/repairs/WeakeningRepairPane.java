package www.ontologyutils.protege.view.repairs;

import java.awt.*;
import java.util.function.Predicate;

import www.ontologyutils.repair.*;
import www.ontologyutils.toolbox.Ontology;

public class WeakeningRepairPane extends AbstractRepairPane {
    public WeakeningRepairPane() {
        setLayout(new FlowLayout(FlowLayout.LEFT));
    }

    @Override
    public String getName() {
        return "Weakening";
    }

    @Override
    public OntologyRepair getRepair(Predicate<Ontology> isRepaired) {
        return new OntologyRepairWeakening(isRepaired);
    }
}
