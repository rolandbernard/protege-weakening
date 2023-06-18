package www.ontologyutils.protege.view.repairs;

import java.util.function.Predicate;

import www.ontologyutils.repair.*;
import www.ontologyutils.toolbox.Ontology;

public class BestMcsRepairPane extends McsRepairPane {
    @Override
    public String getName() {
        return "\"best\" Maximal Consistent Subset";
    }

    @Override
    public OntologyRepair getRepair(Predicate<Ontology> isRepaired) {
        return new OntologyRepairBestMcs(isRepaired, mcsStrategy, o -> (double) o.inferredTaxonomyAxioms().count());
    }
}
