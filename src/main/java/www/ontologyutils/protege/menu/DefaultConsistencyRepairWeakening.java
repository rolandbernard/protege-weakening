package www.ontologyutils.protege.menu;

import www.ontologyutils.repair.*;

public class DefaultConsistencyRepairWeakening extends AutoRepair {
    private OntologyRepair repair = OntologyRepairWeakening.forConsistency();

    @Override
    protected OntologyRepair getOntologyRepair() {
        return repair;
    }
}
