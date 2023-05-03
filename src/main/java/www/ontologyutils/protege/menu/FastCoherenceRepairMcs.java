package www.ontologyutils.protege.menu;

import www.ontologyutils.repair.*;
import www.ontologyutils.repair.OntologyRepairRandomMcs.McsComputationStrategy;
import www.ontologyutils.toolbox.Ontology;

public class FastCoherenceRepairMcs extends AutoRepair {
    private OntologyRepair repair = new OntologyRepairRandomMcs(
            Ontology::isCoherent, McsComputationStrategy.ONE_MCS);

    @Override
    protected OntologyRepair getOntologyRepair() {
        return repair;
    }
}
