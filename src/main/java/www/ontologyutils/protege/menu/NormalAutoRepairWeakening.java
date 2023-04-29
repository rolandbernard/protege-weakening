package www.ontologyutils.protege.menu;

import www.ontologyutils.repair.*;
import www.ontologyutils.repair.OntologyRepairWeakening.*;
import www.ontologyutils.toolbox.Ontology;

public class NormalAutoRepairWeakening extends AutoRepair {
    private final OntologyRepair repair = new OntologyRepairWeakening(
            Ontology::isConsistent, RefOntologyStrategy.SOME_MCS, BadAxiomStrategy.IN_SOME_MUS);

    @Override
    protected OntologyRepair getOntologyRepair() {
        return repair;
    }
}
