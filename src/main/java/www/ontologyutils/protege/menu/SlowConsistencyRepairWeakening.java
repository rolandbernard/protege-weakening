package www.ontologyutils.protege.menu;

import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.repair.*;
import www.ontologyutils.repair.OntologyRepairWeakening.*;
import www.ontologyutils.toolbox.Ontology;

public class SlowConsistencyRepairWeakening extends AutoRepair {
    private OntologyRepair repair = new OntologyRepairWeakening(Ontology::isConsistent, RefOntologyStrategy.RANDOM_MCS, BadAxiomStrategy.IN_LEAST_MCS, AxiomWeakener.FLAG_DEFAULT);

    @Override
    protected OntologyRepair getOntologyRepair() {
        return repair;
    }
}
