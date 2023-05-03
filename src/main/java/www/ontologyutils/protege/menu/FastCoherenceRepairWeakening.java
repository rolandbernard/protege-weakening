package www.ontologyutils.protege.menu;

import www.ontologyutils.repair.*;
import www.ontologyutils.repair.OntologyRepairWeakening.*;
import www.ontologyutils.toolbox.Ontology;

public class FastCoherenceRepairWeakening extends AutoRepair {
    private OntologyRepair repair = new OntologyRepairWeakening(
            Ontology::isCoherent, RefOntologyStrategy.ONE_MCS, BadAxiomStrategy.IN_ONE_MUS);

    @Override
    protected OntologyRepair getOntologyRepair() {
        return repair;
    }
}
