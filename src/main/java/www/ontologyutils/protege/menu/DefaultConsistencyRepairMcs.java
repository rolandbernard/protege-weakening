package www.ontologyutils.protege.menu;

import www.ontologyutils.repair.*;

public class DefaultConsistencyRepairMcs extends AutoRepair {
    private OntologyRepair repair = OntologyRepairRandomMcs.forConsistency();

    @Override
    protected OntologyRepair getOntologyRepair() {
        return repair;
    }
}
