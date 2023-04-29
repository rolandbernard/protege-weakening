package www.ontologyutils.protege.menu;

import www.ontologyutils.repair.*;
import www.ontologyutils.repair.OntologyRepairWeakening.*;
import www.ontologyutils.toolbox.Ontology;

public class FastAutoRepairWeakening extends AutoRepair {
    private final OntologyRepair repair = new OntologyRepairWeakening(
            Ontology::isConsistent, RefOntologyStrategy.ONE_MCS, BadAxiomStrategy.IN_ONE_MUS);

    @Override
    protected OntologyRepair getOntologyRepair() {
        return repair;
    }
}
