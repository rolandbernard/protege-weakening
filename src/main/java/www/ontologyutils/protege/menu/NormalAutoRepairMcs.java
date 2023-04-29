package www.ontologyutils.protege.menu;

import www.ontologyutils.repair.*;
import www.ontologyutils.repair.OntologyRepairRandomMcs.McsComputationStrategy;
import www.ontologyutils.toolbox.Ontology;

public class NormalAutoRepairMcs extends AutoRepair {
    private final OntologyRepair repair = new OntologyRepairRandomMcs(
            Ontology::isConsistent, McsComputationStrategy.SOME_MCS);

    @Override
    protected OntologyRepair getOntologyRepair() {
        return repair;
    }
}
