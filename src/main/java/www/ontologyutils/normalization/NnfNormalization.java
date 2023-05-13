package www.ontologyutils.normalization;

import www.ontologyutils.toolbox.*;

/**
 * Normalization that converts all concepts to negation normal form.
 */
public class NnfNormalization implements OntologyModification {
    @Override
    public void apply(Ontology ontology) {
        var axioms = Utils.toList(ontology.axioms());
        for (var axiom : axioms) {
            ontology.replaceAxiom(axiom, axiom.getNNF());
        }
    }
}
