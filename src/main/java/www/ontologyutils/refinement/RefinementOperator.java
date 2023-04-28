package www.ontologyutils.refinement;

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
    public static final int FLAG_NON_STRICT = AxiomRefinement.FLAG_NON_STRICT;
    public static final int FLAG_NNF_STRICT = AxiomRefinement.FLAG_NNF_STRICT;
    public static final int FLAG_ALC_STRICT = AxiomRefinement.FLAG_ALC_STRICT;
    public static final int FLAG_SROIQ_STRICT = AxiomRefinement.FLAG_SROIQ_STRICT;

    private static class Visitor extends OWLClassExpressionVisitorExAdapter<Stream<OWLClassExpression>> {
        protected final OWLDataFactory df;
        private final Cover way;
        private final Cover back;
        private final int flags;
        private Visitor reverse;

        public Visitor(final Cover way, final Cover back, final int flags) {
            super(null);
            df = Ontology.getDefaultDataFactory();
            this.way = way;
            this.back = back;
            this.flags = flags;
        }

        @Override
        public Stream<OWLClassExpression> visit(final OWLClass concept) {
            return Stream.of();
        }

        @Override
        public Stream<OWLClassExpression> visit(final OWLObjectComplementOf concept) {
            final var operand = concept.getOperand();
            if ((flags & FLAG_NNF_STRICT) != 0 && operand.getClassExpressionType() != ClassExpressionType.OWL_CLASS) {
                throw new IllegalArgumentException("The concept " + concept + " is not in NNF.");
            }
            return reverse.refine(operand)
                    .map(c -> (flags & FLAG_NNF_STRICT) != 0
                            ? c.getComplementNNF()
                            : c.getObjectComplementOf());
        }

        @Override
        public Stream<OWLClassExpression> visit(final OWLObjectIntersectionOf concept) {
            final var conjuncts = concept.getOperandsAsList();
            if ((flags & (FLAG_ALC_STRICT | FLAG_SROIQ_STRICT)) != 0 && conjuncts.size() != 2) {
                throw new IllegalArgumentException("The concept " + concept + " is not a SROIQ concept.");
            }
            return IntStream.range(0, conjuncts.size()).mapToObj(i -> i)
                    .flatMap(i -> refine(conjuncts.get(i))
                            .map(refined -> df.getOWLObjectIntersectionOf(
                                    Utils.replaceInList(conjuncts, i, refined).collect(Collectors.toSet()))));
        }

        @Override
        public Stream<OWLClassExpression> visit(final OWLObjectUnionOf concept) {
            final var disjuncts = concept.getOperandsAsList();
            if ((flags & (FLAG_ALC_STRICT | FLAG_SROIQ_STRICT)) != 0 && disjuncts.size() != 2) {
                throw new IllegalArgumentException("The concept " + concept + " is not a SROIQ concept.");
            }
            return IntStream.range(0, disjuncts.size()).mapToObj(i -> i)
                    .flatMap(i -> refine(disjuncts.get(i))
                            .map(refined -> df.getOWLObjectUnionOf(
                                    Utils.replaceInList(disjuncts, i, refined).collect(Collectors.toSet()))));
        }

        @Override
        public Stream<OWLClassExpression> visit(final OWLObjectAllValuesFrom concept) {
            final var filler = concept.getFiller();
            final var property = concept.getProperty();
            return Stream.concat(
                    refine(filler).map(c -> df.getOWLObjectAllValuesFrom(property, c)),
                    reverse.refine(property).map(r -> df.getOWLObjectAllValuesFrom(r, filler)));
        }

        @Override
        public Stream<OWLClassExpression> visit(final OWLObjectSomeValuesFrom concept) {
            final var filler = concept.getFiller();
            final var property = concept.getProperty();
            return Stream.concat(
                    refine(filler).map(c -> df.getOWLObjectSomeValuesFrom(property, c)),
                    refine(property).map(r -> df.getOWLObjectSomeValuesFrom(r, filler)));
        }

        @Override
        public Stream<OWLClassExpression> visit(final OWLObjectHasSelf concept) {
            if ((flags & FLAG_ALC_STRICT) != 0) {
                throw new IllegalArgumentException("The concept " + concept + " is not an ALC concept.");
            }
            final var property = concept.getProperty();
            return refine(property).map(r -> df.getOWLObjectHasSelf(r));
        }

        @Override
        public Stream<OWLClassExpression> visit(final OWLObjectMaxCardinality concept) {
            if ((flags & FLAG_ALC_STRICT) != 0) {
                throw new IllegalArgumentException("The concept " + concept + " is not an ALC concept.");
            }
            final var number = concept.getCardinality();
            final var filler = concept.getFiller();
            final var property = concept.getProperty();
            return Stream.concat(
                    reverse.refine(filler).map(c -> df.getOWLObjectMaxCardinality(number, property, c)),
                    Stream.concat(
                            reverse.refine(property).map(r -> df.getOWLObjectMaxCardinality(number, r, filler)),
                            way.apply(number).map(n -> df.getOWLObjectMaxCardinality(n, property, filler))));
        }

        @Override
        public Stream<OWLClassExpression> visit(final OWLObjectMinCardinality concept) {
            if ((flags & FLAG_ALC_STRICT) != 0) {
                throw new IllegalArgumentException("The concept " + concept + " is not an ALC concept.");
            }
            final var number = concept.getCardinality();
            final var filler = concept.getFiller();
            final var property = concept.getProperty();
            return Stream.concat(
                    refine(filler).map(c -> df.getOWLObjectMinCardinality(number, property, c)),
                    Stream.concat(
                            refine(property).map(r -> df.getOWLObjectMinCardinality(number, r, filler)),
                            back.apply(number).map(n -> df.getOWLObjectMinCardinality(n, property, filler))));
        }

        @Override
        public Stream<OWLClassExpression> visit(final OWLObjectExactCardinality concept) {
            if ((flags & (FLAG_ALC_STRICT | FLAG_SROIQ_STRICT)) != 0) {
                throw new IllegalArgumentException("The concept " + concept + " is not a SROIQ concept.");
            }
            final var number = concept.getCardinality();
            final var filler = concept.getFiller();
            final var property = concept.getProperty();
            final var minCard = df.getOWLObjectMinCardinality(number, property, filler);
            final var maxCard = df.getOWLObjectMaxCardinality(number, property, filler);
            return Stream.concat(
                    refine(minCard).map(minPart -> df.getOWLObjectIntersectionOf(minPart, maxCard)),
                    refine(maxCard).map(maxPart -> df.getOWLObjectIntersectionOf(minCard, maxPart)));
        }

        @Override
        public Stream<OWLClassExpression> visit(final OWLObjectOneOf concept) {
            if ((flags & FLAG_ALC_STRICT) != 0) {
                throw new IllegalArgumentException("The concept " + concept + " is not an ALC concept.");
            }
            return Stream.of();
        }

        @Override
        public Stream<OWLClassExpression> doDefault(final OWLClassExpression concept) throws IllegalArgumentException {
            if ((flags & (FLAG_ALC_STRICT | FLAG_SROIQ_STRICT)) != 0) {
                throw new IllegalArgumentException("The concept " + concept + " is not a SROIQ concept.");
            } else {
                return Stream.of();
            }
        }

        public Stream<OWLClassExpression> refine(final OWLClassExpression concept) throws IllegalArgumentException {
            // Since all rules include {@code way.apply(concept)} we perform this operation
            // here.
            return Stream.concat(way.apply(concept), concept.accept(this)).distinct();
        }

        public Stream<OWLObjectPropertyExpression> refine(final OWLObjectPropertyExpression role) {
            if ((flags & FLAG_ALC_STRICT) != 0) {
                return Stream.of(role);
            } else {
                return way.apply(role);
            }
        }
    }

    private final Visitor visitor;

    /**
     * Create a new refinement operator.
     *
     * @param way
     * @param back
     * @param flags
     *            Bitset containing flags for restricting the implementation. If
     *            FLAG_ALC_STRICT is set, an exception will be raised if a concept
     *            is not valid in ALC. If FLAG_NNF_STRICT is set, the input must
     *            be in NNF and the output will also be in NNF.
     */
    public RefinementOperator(final Cover way, final Cover back, final int flags) {
        visitor = new Visitor(way, back, flags);
        visitor.reverse = new Visitor(back, way, flags);
        visitor.reverse.reverse = visitor;
    }

    public RefinementOperator(final Cover way, final Cover back) {
        this(way, back, FLAG_NON_STRICT);
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
    public Stream<OWLClassExpression> refine(final OWLClassExpression concept) throws IllegalArgumentException {
        return visitor.refine(concept);
    }

    /**
     * Apply refinement to a role. This is equivalent to simply applying the way
     * cover.
     *
     * @param role
     *            The role that should be refined.
     * @return A stream of all refinements of {@code role} using the covers.
     */
    public Stream<OWLObjectPropertyExpression> refine(final OWLObjectPropertyExpression role) {
        return visitor.refine(role);
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
    public Stream<OWLClassExpression> refineReverse(final OWLClassExpression concept) throws IllegalArgumentException {
        return visitor.reverse.refine(concept);
    }

    /**
     * Apply reverse refinement to a role. This is equivalent to simply applying the
     * way cover.
     *
     * @param role
     *            The role that should be refined.
     * @return A stream of all refinements of {@code role} using the covers.
     */
    public Stream<OWLObjectPropertyExpression> refineReverse(final OWLObjectPropertyExpression role) {
        return visitor.reverse.refine(role);
    }
}
