package www.ontologyutils.refinement;

import java.util.*;
import java.util.stream.*;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.OWLClassExpressionVisitorExAdapter;

import www.ontologyutils.refinement.Covers.Cover;
import www.ontologyutils.toolbox.Ontology;
import www.ontologyutils.toolbox.Utils;

/**
 * Implements a abstract refinement operator that given the upward and downward
 * cover can be used for generalization and specialization operators. Flags are
 * provided for conforming closer to the definitions presented in the paper.
 *
 * The implementation is based on the approach presented in Troquard, Nicolas,
 * et al. "Repairing ontologies via axiom weakening." Proceedings of the AAAI
 * Conference on Artificial Intelligence. Vol. 32. No. 1. 2018. Table 1.
 */
public class RefinementOperator {
    // These are set to be equal to those in AxiomRefinement so they can be passed
    // through.
    /**
     * Default, do not apply any strict constraints.
     */
    public static final int FLAG_DEFAULT = AxiomRefinement.FLAG_DEFAULT;
    /**
     * Accept and produce only axioms with concepts in negation normal form.
     */
    public static final int FLAG_NNF_STRICT = AxiomRefinement.FLAG_NNF_STRICT;
    /**
     * Accept only axioms that have direct equivalents in ALC.
     */
    public static final int FLAG_ALC_STRICT = AxiomRefinement.FLAG_ALC_STRICT;
    /**
     * Accept only axioms that have direct equivalents in SROIQ.
     */
    public static final int FLAG_SROIQ_STRICT = AxiomRefinement.FLAG_ALC_STRICT;
    /**
     * Treat intersection and union operands as lists and allow singleton sets.
     */
    public static final int FLAG_OWL2_SINGLE_OPERANDS = AxiomRefinement.FLAG_OWL2_SINGLE_OPERANDS;

    private static class Visitor extends OWLClassExpressionVisitorExAdapter<Stream<OWLClassExpression>> {
        protected OWLDataFactory df;
        private Cover way;
        private Cover back;
        private int flags;
        private Visitor reverse;

        public Visitor(Cover way, Cover back, int flags) {
            super(null);
            df = Ontology.getDefaultDataFactory();
            this.way = way;
            this.back = back;
            this.flags = flags;
        }

        @Override
        public Stream<OWLClassExpression> visit(OWLClass concept) {
            return Stream.of();
        }

        @Override
        public Stream<OWLClassExpression> visit(OWLObjectComplementOf concept) {
            var operand = concept.getOperand();
            if ((flags & FLAG_NNF_STRICT) != 0 && operand.getClassExpressionType() != ClassExpressionType.OWL_CLASS) {
                throw new IllegalArgumentException("The concept " + concept + " is not in NNF.");
            }
            return reverse.refine(operand)
                    .map(c -> (flags & FLAG_NNF_STRICT) != 0
                            ? c.getComplementNNF()
                            : c.getObjectComplementOf());
        }

        @Override
        public Stream<OWLClassExpression> visit(OWLObjectIntersectionOf concept) {
            var conjuncts = concept.getOperandsAsList();
            if ((flags & (FLAG_ALC_STRICT | FLAG_SROIQ_STRICT)) != 0 && conjuncts.size() != 2) {
                throw new IllegalArgumentException("The concept " + concept + " is not a SROIQ concept.");
            }
            return IntStream.range(0, conjuncts.size()).mapToObj(i -> i)
                    .flatMap(i -> refine(conjuncts.get(i))
                            .map(refined -> {
                                var newConjunctsList = Utils.replaceInList(conjuncts, i, refined);
                                var newConjunctsSet = new LinkedHashSet<>(newConjunctsList);
                                if ((flags & FLAG_OWL2_SINGLE_OPERANDS) != 0) {
                                    return df.getOWLObjectIntersectionOf(newConjunctsSet);
                                } else {
                                    if (newConjunctsSet.size() > 1) {
                                        return df.getOWLObjectIntersectionOf(newConjunctsSet);
                                    } else {
                                        return newConjunctsSet.iterator().next();
                                    }
                                }
                            }));
        }

        @Override
        public Stream<OWLClassExpression> visit(OWLObjectUnionOf concept) {
            var disjuncts = concept.getOperandsAsList();
            if ((flags & (FLAG_ALC_STRICT | FLAG_SROIQ_STRICT)) != 0 && disjuncts.size() != 2) {
                throw new IllegalArgumentException("The concept " + concept + " is not a SROIQ concept.");
            }
            return IntStream.range(0, disjuncts.size()).mapToObj(i -> i)
                    .flatMap(i -> refine(disjuncts.get(i))
                            .map(refined -> {
                                var newDisjunctsList = Utils.replaceInList(disjuncts, i, refined);
                                var newDisjunctsSet = new LinkedHashSet<>(newDisjunctsList);
                                if ((flags & FLAG_OWL2_SINGLE_OPERANDS) != 0) {
                                    return df.getOWLObjectUnionOf(newDisjunctsSet);
                                } else {
                                    if (newDisjunctsSet.size() > 1) {
                                        return df.getOWLObjectUnionOf(newDisjunctsSet);
                                    } else {
                                        return newDisjunctsSet.iterator().next();
                                    }
                                }
                            }));
        }

        @Override
        public Stream<OWLClassExpression> visit(OWLObjectAllValuesFrom concept) {
            var filler = concept.getFiller();
            var property = concept.getProperty();
            return Stream.concat(
                    refine(filler).map(c -> df.getOWLObjectAllValuesFrom(property, c)),
                    reverse.refine(property, false).map(r -> df.getOWLObjectAllValuesFrom(r, filler)));
        }

        @Override
        public Stream<OWLClassExpression> visit(OWLObjectSomeValuesFrom concept) {
            var filler = concept.getFiller();
            var property = concept.getProperty();
            return Stream.concat(
                    refine(filler).map(c -> df.getOWLObjectSomeValuesFrom(property, c)),
                    refine(property, false).map(r -> df.getOWLObjectSomeValuesFrom(r, filler)));
        }

        @Override
        public Stream<OWLClassExpression> visit(OWLObjectHasSelf concept) {
            if ((flags & FLAG_ALC_STRICT) != 0) {
                throw new IllegalArgumentException("The concept " + concept + " is not an ALC concept.");
            }
            var property = concept.getProperty();
            return refine(property, true).map(r -> df.getOWLObjectHasSelf(r));
        }

        @Override
        public Stream<OWLClassExpression> visit(OWLObjectMaxCardinality concept) {
            if ((flags & FLAG_ALC_STRICT) != 0) {
                throw new IllegalArgumentException("The concept " + concept + " is not an ALC concept.");
            }
            var number = concept.getCardinality();
            var filler = concept.getFiller();
            var property = concept.getProperty();
            return Stream.concat(
                    reverse.refine(filler).map(c -> df.getOWLObjectMaxCardinality(number, property, c)),
                    Stream.concat(
                            reverse.refine(property, true).map(r -> df.getOWLObjectMaxCardinality(number, r, filler)),
                            way.apply(number).map(n -> df.getOWLObjectMaxCardinality(n, property, filler))));
        }

        @Override
        public Stream<OWLClassExpression> visit(OWLObjectMinCardinality concept) {
            if ((flags & FLAG_ALC_STRICT) != 0) {
                throw new IllegalArgumentException("The concept " + concept + " is not an ALC concept.");
            }
            var number = concept.getCardinality();
            var filler = concept.getFiller();
            var property = concept.getProperty();
            return Stream.concat(
                    refine(filler).map(c -> df.getOWLObjectMinCardinality(number, property, c)),
                    Stream.concat(
                            refine(property, true).map(r -> df.getOWLObjectMinCardinality(number, r, filler)),
                            back.apply(number).map(n -> df.getOWLObjectMinCardinality(n, property, filler))));
        }

        @Override
        public Stream<OWLClassExpression> visit(OWLObjectExactCardinality concept) {
            if ((flags & (FLAG_ALC_STRICT | FLAG_SROIQ_STRICT)) != 0) {
                throw new IllegalArgumentException("The concept " + concept + " is not a SROIQ concept.");
            }
            var number = concept.getCardinality();
            var filler = concept.getFiller();
            var property = concept.getProperty();
            var minCard = df.getOWLObjectMinCardinality(number, property, filler);
            var maxCard = df.getOWLObjectMaxCardinality(number, property, filler);
            return Stream.concat(
                    refine(minCard).map(minPart -> df.getOWLObjectIntersectionOf(minPart, maxCard)),
                    refine(maxCard).map(maxPart -> df.getOWLObjectIntersectionOf(minCard, maxPart)));
        }

        @Override
        public Stream<OWLClassExpression> visit(OWLObjectOneOf concept) {
            if ((flags & FLAG_ALC_STRICT) != 0) {
                throw new IllegalArgumentException("The concept " + concept + " is not an ALC concept.");
            }
            return Stream.of();
        }

        @Override
        public Stream<OWLClassExpression> doDefault(OWLClassExpression obj) {
            var concept = (OWLClassExpression) obj;
            if ((flags & (FLAG_ALC_STRICT | FLAG_SROIQ_STRICT)) != 0) {
                throw new IllegalArgumentException("The concept " + concept + " is not a SROIQ concept.");
            } else {
                return Stream.of();
            }
        }

        public Stream<OWLClassExpression> refine(OWLClassExpression concept) throws IllegalArgumentException {
            // Since all rules include {@code way.apply(concept)} we perform this operation
            // here.
            return Stream.concat(way.apply(concept), concept.accept(this)).distinct();
        }

        public Stream<OWLObjectPropertyExpression> refine(OWLObjectPropertyExpression role, boolean simple) {
            if ((flags & FLAG_ALC_STRICT) != 0) {
                return Stream.of(role);
            } else {
                return way.apply(role, simple);
            }
        }
    }

    private Visitor visitor;

    /**
     * Create a new refinement operator.
     *
     * @param way
     *            For generalization the upward cover, for specialization the
     *            downward cover.
     * @param back
     *            For generalization the downward cover, for specialization the
     *            upward cover.
     * @param flags
     *            Bitset containing flags for restricting the implementation. If
     *            FLAG_ALC_STRICT is set, an exception will be raised if a concept
     *            is not valid in ALC. If FLAG_NNF_STRICT is set, the input must
     *            be in NNF and the output will also be in NNF.
     */
    public RefinementOperator(Cover way, Cover back, int flags) {
        visitor = new Visitor(way, back, flags);
        visitor.reverse = new Visitor(back, way, flags);
        visitor.reverse.reverse = visitor;
    }

    /**
     * Create a new refinement operator, without any strict flags.
     *
     * @param way
     *            For generalization the upward cover, for specialization the
     *            downward cover.
     * @param back
     *            For generalization the downward cover, for specialization the
     *            upward cover.
     */
    public RefinementOperator(Cover way, Cover back) {
        this(way, back, FLAG_DEFAULT);
    }

    /**
     * If this is the generalization operator, then this will return all
     * generalization of {@code concept}.
     * If this is the specialization operator, then this will return all
     * specialization of {@code concept}.
     *
     * @param concept
     *            The concept to which the refinement operator should be
     *            applied.
     * @return A stream with all refinements of {@code concept}.
     * @throws IllegalArgumentException
     *             If the axioms in this ontology are not
     *             supported by the current flags.
     */
    public Stream<OWLClassExpression> refine(OWLClassExpression concept) throws IllegalArgumentException {
        return visitor.refine(concept);
    }

    /**
     * Apply refinement to a role. If {@code simple} is false, this is equivalent to
     * simply applying the way cover.
     *
     * @param role
     *            The role that should be refined.
     * @param simple
     *            true to force the returned role to be simple.
     * @return A stream of all refinements of {@code role} using the covers.
     */
    public Stream<OWLObjectPropertyExpression> refine(OWLObjectPropertyExpression role, boolean simple) {
        return visitor.refine(role, simple);
    }

    /**
     * Apply refinement to a role. This is equivalent of
     * {@code this.refine(role, true)}.
     *
     * @param role
     *            The role that should be refined.
     * @return A stream of all refinements of {@code role} using the covers that are
     *         simple.
     */
    public Stream<OWLObjectPropertyExpression> refine(OWLObjectPropertyExpression role) {
        return this.refine(role, true);
    }

    /**
     * This applies the refinement operator with swapped way and back functions.
     * If this is the generalization operator, then this will return all
     * specializations of {@code concept}.
     * If this is the specialization operator, then this will return all
     * generalization of {@code concept}.
     *
     * @param concept
     *            The concept to which the refinement operator should be
     *            applied.
     * @return A stream with all refinements of {@code concept}.
     * @throws IllegalArgumentException
     *             If the axioms in this ontology are not
     *             supported by the current flags.
     */
    public Stream<OWLClassExpression> corefine(OWLClassExpression concept) throws IllegalArgumentException {
        return visitor.reverse.refine(concept);
    }

    /**
     * Apply refinement to a role. If {@code simple} is false, this is equivalent to
     * simply applying the back cover.
     *
     * @param role
     *            The role that should be refined.
     * @param simple
     *            true to force the returned role to be simple.
     * @return A stream of all refinements of {@code role} using the covers.
     */
    public Stream<OWLObjectPropertyExpression> corefine(OWLObjectPropertyExpression role, boolean simple) {
        return visitor.reverse.refine(role, simple);
    }

    /**
     * Apply refinement to a role. This is equivalent of
     * {@code this.corefine(role, true)}.
     *
     * @param role
     *            The role that should be refined.
     * @return A stream of all refinements of {@code role} using the covers.
     */
    public Stream<OWLObjectPropertyExpression> corefine(OWLObjectPropertyExpression role) {
        return this.corefine(role, true);
    }
}
