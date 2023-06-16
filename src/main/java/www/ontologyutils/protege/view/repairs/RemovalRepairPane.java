package www.ontologyutils.protege.view.repairs;

import java.awt.*;
import java.util.function.Predicate;

import www.ontologyutils.repair.*;
import www.ontologyutils.toolbox.Ontology;

public class RemovalRepairPane extends AbstractRepairPane {
    public RemovalRepairPane() {
        setLayout(new FlowLayout(FlowLayout.LEFT));
    }

    @Override
    public String getName() {
        return "Removal";
    }

    @Override
    public OntologyRepair getRepair(Predicate<Ontology> isRepaired) {
        return new OntologyRepairRemoval(isRepaired);
    }
}
