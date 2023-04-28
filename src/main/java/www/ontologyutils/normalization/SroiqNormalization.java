package www.ontologyutils.normalization;

import www.ontologyutils.toolbox.*;

/**
 * Normalization that converts the OWL ontology to as close as possible to a
 * SROIQ ontology.
 * Running this is a requirement for some of the strict flags in the refinement
 * operator and axiom weakener.
 */
public class SroiqNormalization implements OntologyModification {
    private final TBoxNormalization tBoxNormalization;
    private final ABoxNormalization aBoxNormalization;
    private final RBoxNormalization rBoxNormalization;
    private final ConceptNormalization conceptNormalization;

    public SroiqNormalization(final boolean binaryOperators, final boolean fullEquality) {
        tBoxNormalization = new TBoxNormalization();
        aBoxNormalization = new ABoxNormalization(fullEquality);
        rBoxNormalization = new RBoxNormalization(fullEquality);
        conceptNormalization = new ConceptNormalization(binaryOperators);
    }

    public SroiqNormalization() {
        this(false, false);
    }

    @Override
    public void apply(final Ontology ontology) throws IllegalArgumentException {
        tBoxNormalization.apply(ontology);
        aBoxNormalization.apply(ontology);
        rBoxNormalization.apply(ontology);
        conceptNormalization.apply(ontology);
    }
}
