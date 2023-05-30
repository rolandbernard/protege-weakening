package www.ontologyutils.refinement;

import java.util.*;
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
public abstract class AxiomRefinement {
    /**
     * Default, do not apply any strict constraints.
     */
    public static final int FLAG_DEFAULT = 0;
    /**
     * Accept and produce only axioms with concepts in negation normal form.
     */
    public static final int FLAG_NNF_STRICT = 1 << 0;
    /**
     * Accept only axioms that have direct equivalents in ALC.
     */
    public static final int FLAG_ALC_STRICT = (1 << 1) | AxiomRefinement.FLAG_SROIQ_STRICT;
    /**
     * Accept only axioms that have direct equivalents in SROIQ.
     */
    public static final int FLAG_SROIQ_STRICT = 1 << 2;
    /**
     * Treat intersection and union operands as lists and allow singleton sets.
     * Inverse of what we have in ontologyutils, because the api accepts only sets,
     * unlike in ontologyutils where we use OWL API version 5.
     */
    public static final int FLAG_OWL2_SINGLE_OPERANDS = 1 << 3;
    /**
     * Refine roles only with simple roles, even in a context that allows complex
     * roles. (Implies also the use of simple RIA refinement.)
     */
    public static final int FLAG_SIMPLE_ROLES_STRICT = (1 << 4) | AxiomRefinement.FLAG_RIA_ONLY_SIMPLE;
    /**
     * Do not use a cache for the covers.
     */
    public static final int FLAG_UNCACHED = 1 << 5;
    /**
     * Use only simple roles for role inclusion axiom weakening.
     */
    public static final int FLAG_RIA_ONLY_SIMPLE = 1 << 6;

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
         * must be.
         */
        protected Set<OWLObjectPropertyExpression> simpleRoles;
        /**
         * The order used for restricting the allowed refinements of the role inclusion
         * axioms.
         */
        protected PreorderCache<OWLObjectProperty> regularPreorder;
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
         * @param regularPreorder
         *            A preorder that was produced for the regularity check of a
         *            ontology. The refined axioms are guaranteed to be valid in any
         *            ontology for which this preorder describes the RBox hierarchy.
         * @param flags
         *            Flags that can be used to make the refinement ore strict.
         */
        public Visitor(RefinementOperator up, RefinementOperator down, Set<OWLObjectPropertyExpression> simpleRoles,
                PreorderCache<OWLObjectProperty> regularPreorder, int flags) {
            super(null);
            df = Ontology.getDefaultDataFactory();
            this.up = up;
            this.down = down;
            this.simpleRoles = simpleRoles;
            this.regularPreorder = regularPreorder;
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
                    up.refine(role, false)
                            .map(newRole -> df.getOWLObjectPropertyAssertionAxiom(newRole, subject, object)),
                    Stream.of(axiom, noopAxiom()));
        }

        @Override
        public Stream<OWLAxiom> visit(OWLNegativeObjectPropertyAssertionAxiom axiom) {
            var subject = axiom.getSubject();
            var role = axiom.getProperty();
            var object = axiom.getObject();
            return Stream.concat(
                    down.refine(role, false)
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
                            .map(refined -> {
                                var newConceptsList = Utils.replaceInList(concepts, i, refined);
                                var newConceptsSet = new LinkedHashSet<OWLClassExpression>();
                                for (var concept : newConceptsList) {
                                    while (!newConceptsSet.add(concept)) {
                                        // This is a duplicate created by the refinement. Since this is unfortunately
                                        // not allowed in OWL 2, we use this stupid hack.
                                        concept = concept.getObjectComplementOf().getObjectComplementOf();
                                    }
                                }
                                return df.getOWLDisjointClassesAxiom(newConceptsSet);
                            }));
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

        private boolean allowWeakeningTo(OWLSubObjectPropertyOfAxiom axiom) {
            var sub = axiom.getSubProperty();
            var sup = axiom.getSuperProperty();
            return simpleRoles.contains(sub) || (!simpleRoles.contains(sup)
                    && regularPreorder.assertSuccessor(sub.getNamedProperty(), sup.getNamedProperty()));
        }

        @Override
        public Stream<OWLAxiom> visit(OWLSubObjectPropertyOfAxiom axiom) {
            if ((flags & FLAG_ALC_STRICT) != 0) {
                throw new IllegalArgumentException("The axiom " + axiom + " is not an ALC axiom.");
            }
            if (regularPreorder == null) {
                return Stream.concat(Stream.of(noopAxiom()),
                        Stream.concat(
                                down.refine(axiom.getSubProperty(), true)
                                        .map(role -> df.getOWLSubObjectPropertyOfAxiom(role, axiom.getSuperProperty())),
                                simpleRoles.contains(axiom.getSubProperty())
                                        ? up.refine(axiom.getSuperProperty(), false).map(
                                                role -> df.getOWLSubObjectPropertyOfAxiom(axiom.getSubProperty(), role))
                                        : Stream.of()));
            } else {
                return Stream.concat(Stream.of(noopAxiom()), Stream.concat(
                        down.refine(axiom.getSubProperty(), false)
                                .map(role -> df.getOWLSubObjectPropertyOfAxiom(role, axiom.getSuperProperty())),
                        up.refine(axiom.getSuperProperty(), false)
                                .map(role -> df.getOWLSubObjectPropertyOfAxiom(axiom.getSubProperty(), role)))
                        .filter(ax -> allowWeakeningTo(ax))
                        .map(ax -> (OWLAxiom) ax));
            }
        }

        private boolean allowWeakeningTo(OWLSubPropertyChainOfAxiom axiom) {
            var subs = axiom.getPropertyChain();
            var sup = axiom.getSuperProperty();
            if (simpleRoles.contains(sup)) {
                return false;
            }
            List<OWLObjectPropertyExpression> preds;
            if (subs.size() == 2 && subs.get(0).equals(sup) && subs.get(1).equals(sup)) {
                return true;
            } else if (subs.get(0).equals(sup)) {
                preds = subs.subList(1, subs.size());
            } else if (subs.get(subs.size() - 1).equals(sup)) {
                preds = subs.subList(0, subs.size() - 1);
            } else {
                preds = subs;
            }
            for (var pred : preds) {
                var subName = pred.getNamedProperty();
                var supName = sup.getNamedProperty();
                if (!simpleRoles.contains(pred) && (!regularPreorder.assertSuccessor(subName, supName)
                        || !regularPreorder.denySuccessor(supName, subName))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public Stream<OWLAxiom> visit(OWLSubPropertyChainOfAxiom axiom) {
            if ((flags & FLAG_ALC_STRICT) != 0) {
                throw new IllegalArgumentException("The axiom " + axiom + " is not an ALC axiom.");
            }
            var chain = axiom.getPropertyChain();
            if (regularPreorder == null) {
                return Stream.concat(Stream.of(noopAxiom()),
                        IntStream.range(0, chain.size()).mapToObj(i -> i)
                                .flatMap(i -> down.refine(chain.get(i), true)
                                        .map(role -> df.getOWLSubPropertyChainOfAxiom(
                                                Utils.replaceInList(chain, i, role), axiom.getSuperProperty()))));
            } else {
                return Stream.concat(Stream.of(noopAxiom()), Stream.concat(
                        IntStream.range(0, chain.size()).mapToObj(i -> i)
                                .flatMap(i -> down.refine(chain.get(i), false)
                                        .map(role -> df.getOWLSubPropertyChainOfAxiom(
                                                Utils.replaceInList(chain, i, role), axiom.getSuperProperty()))),
                        up.refine(axiom.getSuperProperty(), false)
                                .map(role -> df.getOWLSubPropertyChainOfAxiom(chain, role)))
                        .filter(ax -> allowWeakeningTo(ax))
                        .map(ax -> (OWLAxiom) ax));
            }
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
                            .flatMap(i -> down.refine(properties.get(i), true)
                                    .map(refined -> {
                                        var newRolesList = Utils.replaceInList(properties, i, refined);
                                        var newRolesSet = new LinkedHashSet<OWLObjectPropertyExpression>();
                                        for (var role : newRolesList) {
                                            while (!newRolesSet.add(role)) {
                                                // This is a duplicate created by the refinement. Since this is
                                                // unfortunately not allowed in OWL 2, we use this stupid hack.
                                                role = role.getInverseProperty().getInverseProperty();
                                            }
                                        }
                                        return df.getOWLDisjointObjectPropertiesAxiom(newRolesSet);
                                    })));
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

    /**
     * @param visitor
     *            The visitor used by this operator.
     */
    protected AxiomRefinement(Visitor visitor) {
        this.visitor = visitor;
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
}
