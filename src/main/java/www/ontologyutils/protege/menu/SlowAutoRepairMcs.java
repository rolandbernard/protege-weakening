package www.ontologyutils.protege.menu;

import www.ontologyutils.repair.*;
import www.ontologyutils.repair.OntologyRepairRandomMcs.McsComputationStrategy;
import www.ontologyutils.toolbox.Ontology;

public class SlowAutoRepairMcs extends AutoRepair {
    private final OntologyRepair repair = new OntologyRepairRandomMcs(
            Ontology::isConsistent, McsComputationStrategy.All_MCS);

    @Override
    protected OntologyRepair getOntologyRepair() {
        return repair;
    }
}
