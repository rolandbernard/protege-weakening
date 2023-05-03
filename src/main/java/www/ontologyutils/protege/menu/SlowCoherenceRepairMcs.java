package www.ontologyutils.protege.menu;

import www.ontologyutils.repair.*;
import www.ontologyutils.repair.OntologyRepairRandomMcs.McsComputationStrategy;
import www.ontologyutils.toolbox.Ontology;

public class SlowCoherenceRepairMcs extends AutoRepair {
    private OntologyRepair repair = new OntologyRepairRandomMcs(
            Ontology::isCoherent, McsComputationStrategy.ALL_MCS);

    @Override
    protected OntologyRepair getOntologyRepair() {
        return repair;
    }
}
