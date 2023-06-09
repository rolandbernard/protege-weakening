package www.ontologyutils.refinement;

import java.util.Set;
import java.util.stream.*;

import org.semanticweb.owlapi.model.*;

import www.ontologyutils.refinement.Covers.Cover;
import www.ontologyutils.toolbox.*;

/**
 * Implementation that can be used for weakening an axiom. Must be closed
 * after usage to free up resources used by the inner {@code Covers} object.
 */
public class AxiomWeakener extends AxiomRefinement {
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
            return df.getOWLSubClassOfAxiom(df.getOWLNothing(), df.getOWLThing());
        }

        @Override
        public Stream<OWLAxiom> visit(OWLSameIndividualAxiom axiom) {
            var individuals = axiom.getIndividualsAsList();
            if (individuals.size() <= 2) {
                return super.visit(axiom);
            } else {
                if ((flags & (FLAG_ALC_STRICT | FLAG_SROIQ_STRICT)) != 0) {
                    throw new IllegalArgumentException("The axiom " + axiom + " is not a SROIQ axiom.");
                }
                return Stream.concat(Stream.of((OWLAxiom) axiom),
                        IntStream.range(0, individuals.size()).mapToObj(i -> i)
                                .map(i -> df.getOWLSameIndividualAxiom(
                                        Utils.toSet(Utils.removeFromList(individuals, i)))));
            }
        }

        @Override
        public Stream<OWLAxiom> visit(OWLDifferentIndividualsAxiom axiom) {
            var individuals = axiom.getIndividualsAsList();
            if (individuals.size() <= 2) {
                return super.visit(axiom);
            } else {
                if ((flags & (FLAG_ALC_STRICT | FLAG_SROIQ_STRICT)) != 0) {
                    throw new IllegalArgumentException("The axiom " + axiom + " is not a SROIQ axiom.");
                }
                return Stream.concat(Stream.of((OWLAxiom) axiom),
                        IntStream.range(0, individuals.size()).mapToObj(i -> i)
                                .map(i -> df.getOWLDifferentIndividualsAxiom(
                                        Utils.toSet(Utils.removeFromList(individuals, i)))));
            }
        }

        @Override
        public Stream<OWLAxiom> visit(OWLEquivalentClassesAxiom axiom) {
            if ((flags & (FLAG_ALC_STRICT | FLAG_SROIQ_STRICT)) != 0) {
                throw new IllegalArgumentException("The axiom " + axiom + " is not a SROIQ axiom.");
            }
            var concepts = axiom.getClassExpressionsAsList();
            if (concepts.size() <= 2) {
                return super.visit(axiom);
            } else {
                return Stream.concat(Stream.of((OWLAxiom) axiom),
                        IntStream.range(0, concepts.size()).mapToObj(i -> i)
                                .map(i -> df
                                        .getOWLEquivalentClassesAxiom(Utils.toSet(Utils.removeFromList(concepts, i)))));
            }
        }

        @Override
        public Stream<OWLAxiom> visit(OWLEquivalentObjectPropertiesAxiom axiom) {
            if ((flags & (FLAG_ALC_STRICT | FLAG_SROIQ_STRICT)) != 0) {
                throw new IllegalArgumentException("The axiom " + axiom + " is not a SROIQ axiom.");
            }
            var properties = Utils.toList(axiom.getProperties().stream());
            if (properties.size() <= 2) {
                return super.visit(axiom);
            } else {
                return Stream.concat(Stream.of((OWLAxiom) axiom),
                        IntStream.range(0, properties.size()).mapToObj(i -> i)
                                .map(i -> df.getOWLEquivalentObjectPropertiesAxiom(
                                        Utils.toSet(Utils.removeFromList(properties, i)))));
            }
        }
    }

    private AxiomWeakener(Cover upCover, Cover downCover, Set<OWLObjectPropertyExpression> simpleRoles,
            PreorderCache<OWLObjectProperty> regularPreorder, int flags) {
        super(new Visitor(new RefinementOperator(upCover, downCover, flags),
                new RefinementOperator(downCover, upCover, flags), simpleRoles, regularPreorder, flags));
    }

    private AxiomWeakener(Covers covers, Set<OWLObjectPropertyExpression> simpleRoles,
            PreorderCache<OWLObjectProperty> regularPreorder, int flags) {
        this((flags & FLAG_UNCACHED) != 0 ? covers.upCover() : covers.upCover().cached(),
                (flags & FLAG_UNCACHED) != 0 ? covers.downCover() : covers.downCover().cached(), simpleRoles,
                regularPreorder, flags);
    }

    private AxiomWeakener(Ontology refOntology, Set<OWLClassExpression> subConcepts,
            Set<OWLObjectPropertyExpression> subRoles, Set<OWLObjectPropertyExpression> simpleRoles,
            PreorderCache<OWLObjectProperty> regularPreorder, int flags) {
        this(new Covers(refOntology, subConcepts,
                (flags & FLAG_NO_ROLE_REFINEMENT) != 0 ? Set.of()
                        : (flags & FLAG_SIMPLE_ROLES_STRICT) != 0 ? simpleRoles : subRoles,
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
    public AxiomWeakener(Ontology refOntology, Ontology fullOntology, int flags) {
        this(refOntology, Utils.toSet(fullOntology.subConcepts()),
                Utils.toSet(fullOntology.subRoles()), Utils.toSet(fullOntology.simpleRoles()),
                (flags & (FLAG_RIA_ONLY_SIMPLE | FLAG_SIMPLE_ROLES_STRICT)) != 0 ? null
                        : fullOntology.regularPreorder(),
                flags);
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
    public AxiomWeakener(Ontology refOntology, Ontology fullOntology) {
        this(refOntology, fullOntology, FLAG_DEFAULT);
    }

    /**
     * @param refOntology
     *            The reference ontology to use for the up and down covers.
     * @param flags
     *            The flags to use.
     */
    public AxiomWeakener(Ontology refOntology, int flags) {
        this(refOntology, refOntology, flags);
    }

    /**
     * Create a new axiom weakener with the given reference ontology.The reference
     * ontology must contain all RBox axioms of all ontologies the weaker axioms
     * are used in, otherwise the resulting axiom is not guaranteed to satisfy
     * global restrictions on roles.
     *
     * @param refOntology
     *            The reference ontology to use for the up and down covers.
     */
    public AxiomWeakener(Ontology refOntology) {
        this(refOntology, refOntology);
    }

    /**
     * Computes all axioms derived by:
     * - for subclass axioms: either specializing the left hand side or generalizing
     * the right hand side.
     * - for assertion axioms: generalizing the concept.
     *
     * @param axiom
     *            The axiom for which we want to find weaker axioms.
     * @return A stream of axioms that are all weaker than {@code axiom}.
     */
    public Stream<OWLAxiom> weakerAxioms(OWLAxiom axiom) {
        return refineAxioms(axiom);
    }
}
