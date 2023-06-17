package www.ontologyutils.protege.view.repairs;

import java.util.function.Predicate;

import www.ontologyutils.repair.*;
import www.ontologyutils.toolbox.Ontology;

public class MctsWeakeningRepairPane extends BestOfKWeakeningRepairPane {
    @Override
    public String getName() {
        return "\"best\" via Weakening Tree Search";
    }

    @Override
    public OntologyRepair getRepair(Predicate<Ontology> isRepaired) {
        return new OntologyRepairMctsWeakening(isRepaired, refOntologySource, badAxiomSource, weakeningFlags,
                enhanceRef, o -> (double) o.inferredTaxonomyAxioms().count(), numberOfRounds);
    }
}
