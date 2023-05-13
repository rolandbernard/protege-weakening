package www.ontologyutils.refinement;

import java.util.Set;
import java.util.stream.*;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.OWLAxiomVisitorExAdapter;

import www.ontologyutils.toolbox.*;

/**
 * Abstract base class for axiom weakener and axiom strengthener. Most of the
 * functionality is here, only some axiom types require more careful
 * considerations.
 *
 * The implementation is based on the approach presented in Troquard, Nicolas,
 * et al. "Repairing ontologies via axiom weakening." Proceedings of the AAAI
 * Conference on Artificial Intelligence. Vol. 32. No. 1. 2018. Definition 3.
 *
 * The implementation for SROIQ axioms is based on the approach presented in
 * Confalonieri, R., Galliani, P., Kutz, O., Porello, D., Righetti, G., &amp;
 * Toquard, N. (2020). Towards even more irresistible axiom weakening.
 */
public abstract class AxiomRefinement implements AutoCloseable {
    /**
     * Default, do not apply any strict constraints.
     */
    public static final int FLAG_NON_STRICT = 0;
    /**
     * Accept and produce only axioms with concepts in negation normal form.
     */
    public static final int FLAG_NNF_STRICT = 1 << 0;
    /**
     * Accept only axioms that have direct equivalents in ALC.
     */
    public static final int FLAG_ALC_STRICT = 1 << 1;
    /**
     * Accept only axioms that have direct equivalents in SROIQ.
     */
    public static final int FLAG_SROIQ_STRICT = 1 << 2;

    /**
     * Visitor implementing the actual weakening.
     */
    protected static abstract class Visitor extends OWLAxiomVisitorExAdapter<Stream<OWLAxiom>> {
        /**
         * OWL data factory to use for the creation of new axioms.
         */
        protected OWLDataFactory df;
        /**
         * The refinement operator to use for "upward" refinement.
         */
        protected RefinementOperator up;
        /**
         * The refinement operator to use for "downward" refinement.
         */
        protected RefinementOperator down;
        /**
         * The set of all roles that is guaranteed to be simple and whose simplicity
         * must be
         */
        protected Set<OWLObjectPropertyExpression> simpleRoles;
        /**
         * The flags.
         */
        protected int flags;

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
        public Visitor(RefinementOperator up, RefinementOperator down,
                Set<OWLObjectPropertyExpression> simpleRoles, int flags) {
            super(null);
            df = Ontology.getDefaultDataFactory();
            this.up = up;
            this.down = down;
            this.simpleRoles = simpleRoles;
            this.flags = flags;
        }

        /**
         * @return The axiom that should be used as a last resort, in case no other
         *         weakening/strengthening is available.
         */
        protected abstract OWLAxiom noopAxiom();

        @Override
        public Stream<OWLAxiom> visit(OWLDeclarationAxiom axiom) {
            // These axioms can not be weakened and must not be removed.
            return Stream.of(axiom);
        }

        @Override
        public Stream<OWLAxiom> visit(OWLSubClassOfAxiom axiom) {
            var subclass = axiom.getSubClass();
            var superclass = axiom.getSuperClass();
            return Stream.concat(
                    down.refine(subclass)
                            .map(newSubclass -> df.getOWLSubClassOfAxiom(newSubclass, superclass)),
                    up.refine(superclass)
                            .map(newSuperclass -> df.getOWLSubClassOfAxiom(subclass, newSuperclass)));
        }

        @Override
        public Stream<OWLAxiom> visit(OWLClassAssertionAxiom axiom) {
            var concept = axiom.getClassExpression();
            var individual = axiom.getIndividual();
            return up.refine(concept)
                    .map(newConcept -> df.getOWLClassAssertionAxiom(newConcept, individual));
        }

        @Override
        public Stream<OWLAxiom> visit(OWLObjectPropertyAssertionAxiom axiom) {
            var subject = axiom.getSubject();
            var role = axiom.getProperty();
            var object = axiom.getObject();
            return Stream.concat(
                    up.refine(role)
                            .map(newRole -> df.getOWLObjectPropertyAssertionAxiom(newRole, subject, object)),
                    Stream.of(axiom, noopAxiom()));
        }

        @Override
        public Stream<OWLAxiom> visit(OWLNegativeObjectPropertyAssertionAxiom axiom) {
            var subject = axiom.getSubject();
            var role = axiom.getProperty();
            var object = axiom.getObject();
            return Stream.concat(
                    down.refine(role)
                            .map(newRole -> df.getOWLNegativeObjectPropertyAssertionAxiom(newRole, subject, object)),
                    Stream.of(axiom, noopAxiom()));
        }

        @Override
        public Stream<OWLAxiom> visit(OWLSameIndividualAxiom axiom) {
            if ((flags & (FLAG_ALC_STRICT | FLAG_SROIQ_STRICT)) != 0 && axiom.getIndividuals().size() > 2) {
                throw new IllegalArgumentException("The axiom " + axiom + " is not a SROIQ axiom.");
            }
            return Stream.of(axiom, noopAxiom());
        }

        @Override
        public Stream<OWLAxiom> visit(OWLDifferentIndividualsAxiom axiom) {
            if ((flags & (FLAG_ALC_STRICT | FLAG_SROIQ_STRICT)) != 0 && axiom.getIndividuals().size() > 2) {
                throw new IllegalArgumentException("The axiom " + axiom + " is not a SROIQ axiom.");
            }
            return Stream.of(axiom, noopAxiom());
        }

        @Override
        public Stream<OWLAxiom> visit(OWLDisjointClassesAxiom axiom) {
            if ((flags & (FLAG_ALC_STRICT | FLAG_SROIQ_STRICT)) != 0) {
                throw new IllegalArgumentException("The axiom " + axiom + " is not a SROIQ axiom.");
            }
            var concepts = axiom.getClassExpressionsAsList();
            return IntStream.range(0, concepts.size()).mapToObj(i -> i)
                    .flatMap(i -> down.refine(concepts.get(i))
                            .map(refined -> df.getOWLDisjointClassesAxiom(Utils.toSet(Utils.replaceInList(concepts, i, refined)))));
        }

        @Override
        public Stream<OWLAxiom> visit(OWLObjectPropertyDomainAxiom axiom) {
            if ((flags & (FLAG_ALC_STRICT | FLAG_SROIQ_STRICT)) != 0) {
                throw new IllegalArgumentException("The axiom " + axiom + " is not a SROIQ axiom.");
            }
            var concept = axiom.getDomain();
            var property = axiom.getProperty();
            return up.refine(concept)
                    .map(newConcept -> df.getOWLObjectPropertyDomainAxiom(property, newConcept));
        }

        @Override
        public Stream<OWLAxiom> visit(OWLObjectPropertyRangeAxiom axiom) {
            if ((flags & (FLAG_ALC_STRICT | FLAG_SROIQ_STRICT)) != 0) {
                throw new IllegalArgumentException("The axiom " + axiom + " is not a SROIQ axiom.");
            }
            var concept = axiom.getRange();
            var property = axiom.getProperty();
            return up.refine(concept)
                    .map(newConcept -> df.getOWLObjectPropertyRangeAxiom(property, newConcept));
        }

        @Override
        public Stream<OWLAxiom> visit(OWLSubObjectPropertyOfAxiom axiom) {
            if ((flags & FLAG_ALC_STRICT) != 0) {
                throw new IllegalArgumentException("The axiom " + axiom + " is not an ALC axiom.");
            }
            return Stream.concat(Stream.of(noopAxiom()),
                    Stream.concat(
                            down.refine(axiom.getSubProperty())
                                    .map(role -> df.getOWLSubObjectPropertyOfAxiom(role, axiom.getSuperProperty())),
                            simpleRoles.contains(axiom.getSubProperty())
                                    ? up.refine(axiom.getSuperProperty())
                                            .map(role -> df.getOWLSubObjectPropertyOfAxiom(axiom.getSubProperty(),
                                                    role))
                                    : Stream.of()));
        }

        @Override
        public Stream<OWLAxiom> visit(OWLSubPropertyChainOfAxiom axiom) {
            if ((flags & FLAG_ALC_STRICT) != 0) {
                throw new IllegalArgumentException("The axiom " + axiom + " is not an ALC axiom.");
            }
            var chain = axiom.getPropertyChain();
            return Stream.concat(Stream.of(noopAxiom()),
                    IntStream.range(0, chain.size()).mapToObj(i -> i)
                            .flatMap(i -> down.refine(chain.get(i))
                                    .map(role -> df.getOWLSubPropertyChainOfAxiom(
                                            Utils.toList(Utils.replaceInList(chain, i, role)),
                                            axiom.getSuperProperty()))));
        }

        @Override
        public Stream<OWLAxiom> visit(OWLDisjointObjectPropertiesAxiom axiom) {
            if ((flags & FLAG_ALC_STRICT) != 0) {
                throw new IllegalArgumentException("The axiom " + axiom + " is not an ALC axiom.");
            } else if ((flags & FLAG_SROIQ_STRICT) != 0 && axiom.getProperties().size() > 2) {
                throw new IllegalArgumentException("The axiom " + axiom + " is not a SROIQ axiom.");
            }
            var properties = Utils.toList(axiom.getProperties().stream());
            return Stream.concat(Stream.of(noopAxiom()),
                    IntStream.range(0, properties.size()).mapToObj(i -> i)
                            .flatMap(i -> down.refine(properties.get(i))
                                    .map(role -> df.getOWLDisjointObjectPropertiesAxiom(
                                            Utils.toSet(Utils.replaceInList(properties, i, role))))));
        }

        @Override
        public Stream<OWLAxiom> doDefault(OWLAxiom axiom) {
            var ax = (OWLAxiom) axiom;
            if ((flags & (FLAG_ALC_STRICT | FLAG_SROIQ_STRICT)) != 0) {
                throw new IllegalArgumentException("The axiom " + ax + " is not a SROIQ axiom.");
            } else {
                return Stream.of(ax, noopAxiom());
            }
        }
    }

    private Visitor visitor;
    private Covers covers;

    /**
     * @param visitor
     *            The visitor used by this operator.
     * @param covers
     *            The covers used by this operator. Ownership of the covers object
     *            is transferred to this object, and it will be closed when the new
     *            axiom refinement object is closed.
     */
    protected AxiomRefinement(Visitor visitor, Covers covers) {
        this.visitor = visitor;
        this.covers = covers;
    }

    /**
     * Computes all axioms derived by from {@code axiom} using the refinement
     * operators.
     *
     * @param axiom
     *            The axiom for which we want to find weaker/stronger axioms.
     * @return A stream of axioms that are all weaker/stronger than {@code axiom}.
     */
    public Stream<OWLAxiom> refineAxioms(OWLAxiom axiom) {
        return axiom.accept(visitor).distinct();
    }

    @Override
    public void close() {
        covers.close();
    }
}
