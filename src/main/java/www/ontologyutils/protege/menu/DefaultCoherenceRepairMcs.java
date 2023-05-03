package www.ontologyutils.protege.menu;

import www.ontologyutils.repair.*;

public class DefaultCoherenceRepairMcs extends AutoRepair {
    private OntologyRepair repair = OntologyRepairRandomMcs.forCoherence();

    @Override
    protected OntologyRepair getOntologyRepair() {
        return repair;
    }
}
