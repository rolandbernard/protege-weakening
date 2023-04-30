package www.ontologyutils.protege.menu;

import www.ontologyutils.repair.*;
import www.ontologyutils.repair.OntologyRepairWeakening.*;
import www.ontologyutils.toolbox.Ontology;

public class SlowCoherenceRepairWeakening extends AutoRepair {
    private final OntologyRepair repair = new OntologyRepairWeakening(
            Ontology::isCoherent, RefOntologyStrategy.RANDOM_MCS, BadAxiomStrategy.IN_LEAST_MCS);

    @Override
    protected OntologyRepair getOntologyRepair() {
        return repair;
    }
}
