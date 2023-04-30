package www.ontologyutils.protege.menu;

import www.ontologyutils.repair.*;

public class DefaultCoherenceRepairWeakening extends AutoRepair {
    private final OntologyRepair repair = OntologyRepairWeakening.forCoherence();

    @Override
    protected OntologyRepair getOntologyRepair() {
        return repair;
    }
}
