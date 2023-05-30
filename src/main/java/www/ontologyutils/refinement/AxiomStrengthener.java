package www.ontologyutils.refinement;

import java.util.Set;
import java.util.stream.*;

import org.semanticweb.owlapi.model.*;

import www.ontologyutils.refinement.Covers.Cover;
import www.ontologyutils.toolbox.*;

/**
 * Implementation that can be used for strengthening an axiom. Must be closed
 * after usage to free up resources used by the inner {@code Covers} object.
 */
public class AxiomStrengthener extends AxiomRefinement {
    private static class Visitor extends AxiomRefinement.Visitor {
        /**
         * @param up
         *            The "upward"-refinement.
         * @param down
         *            The "downward"-refinement.
         * @param simpleRoles
         *            The set of simple roles. These are used for deciding whether it is
         *            safe to refine a role inclusion axiom.
         * @param flags
         *            Flags that can be used to make the refinement ore strict.
         */
        public Visitor(RefinementOperator up, RefinementOperator down, Set<OWLObjectPropertyExpression> simpleRoles,
                PreorderCache<OWLObjectProperty> regularPreorder, int flags) {
            super(up, down, simpleRoles, regularPreorder, flags);
        }

        @Override
        protected OWLAxiom noopAxiom() {
            return df.getOWLSubClassOfAxiom(df.getOWLThing(), df.getOWLNothing());
        }
    }

    private AxiomStrengthener(Cover upCover, Cover downCover, Set<OWLObjectPropertyExpression> simpleRoles,
            PreorderCache<OWLObjectProperty> regularPreorder, int flags) {
        super(new Visitor(new RefinementOperator(downCover, upCover, flags),
                new RefinementOperator(upCover, downCover, flags), simpleRoles, regularPreorder, flags));
    }

    private AxiomStrengthener(Covers covers, Set<OWLObjectPropertyExpression> simpleRoles,
            PreorderCache<OWLObjectProperty> regularPreorder, int flags) {
        this((flags & FLAG_UNCACHED) != 0 ? covers.upCover() : covers.upCover().cached(),
                (flags & FLAG_UNCACHED) != 0 ? covers.downCover() : covers.downCover().cached(), simpleRoles,
                regularPreorder, flags);
    }

    private AxiomStrengthener(Ontology refOntology, Set<OWLClassExpression> subConcepts,
            Set<OWLObjectPropertyExpression> subRoles, Set<OWLObjectPropertyExpression> simpleRoles,
            PreorderCache<OWLObjectProperty> regularPreorder, int flags) {
        this(new Covers(refOntology, subConcepts, (flags & FLAG_SIMPLE_ROLES_STRICT) != 0 ? simpleRoles : subRoles,
                simpleRoles, (flags & FLAG_UNCACHED) != 0), simpleRoles, regularPreorder, flags);
    }

    /**
     * @param refOntology
     *            The reference ontology to use for the up and down covers.
     * @param fullOntology
     *            The maximal ontology in which the weaker axioms will be
     *            used in.
     * @param flags
     *            The flags to use.
     */
    public AxiomStrengthener(Ontology refOntology, Ontology fullOntology, int flags) {
        this(refOntology, Utils.toSet(fullOntology.subConcepts()),
                Utils.toSet(fullOntology.subRoles()), Utils.toSet(fullOntology.simpleRoles()),
                (flags & FLAG_RIA_ONLY_SIMPLE) != 0 ? null : fullOntology.regularPreorder(), flags);
    }

    /**
     * Create a new axiom weakener with the given reference ontology.
     *
     * @param refOntology
     *            The reference ontology to use for the up and down covers.
     * @param fullOntology
     *            The maximal ontology in which the weaker axioms will be
     *            used in.
     */
    public AxiomStrengthener(Ontology refOntology, Ontology fullOntology) {
        this(refOntology, fullOntology, FLAG_DEFAULT);
    }

    /**
     * @param refOntology
     *            The reference ontology to use for the up and down covers.
     * @param flags
     *            The flags to use.
     */
    public AxiomStrengthener(Ontology refOntology, int flags) {
        this(refOntology, refOntology, flags);
    }

    /**
     * Create a new axiom strengthener with the given reference ontology.The
     * reference ontology must contain all RBox axioms of all ontologies the
     * stronger axioms are used in, otherwise the resulting axiom is not guaranteed
     * to satisfy global restrictions on roles.
     *
     * @param refOntology
     *            The reference ontology to use for the up and down covers.
     */
    public AxiomStrengthener(Ontology refOntology) {
        this(refOntology, refOntology);
    }

    /**
     * Computes all axioms derived by:
     * - for subclass axioms: either generalizing the left hand side or specializing
     * the right hand side.
     * - for assertion axioms: specializing the concept.
     *
     * @param axiom
     *            The axiom for which we want to find stronger axioms.
     * @return A stream of axioms that are all stronger than {@code axiom}.
     */
    public Stream<OWLAxiom> strongerAxioms(OWLAxiom axiom) {
        return refineAxioms(axiom);
    }
}
