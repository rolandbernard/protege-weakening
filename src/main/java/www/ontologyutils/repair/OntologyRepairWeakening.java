package www.ontologyutils.repair;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import org.semanticweb.owlapi.model.*;

import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.toolbox.*;

/**
 * An implementation of {@code OntologyRepair} following closely (but not
 * strictly) the axiom weakening approach described in Nicolas Troquard, Roberto
 * Confalonieri, Pietro Galliani, Rafael Peñaloza, Daniele Porello, Oliver Kutz:
 * "Repairing Ontologies via Axiom Weakening", AAAI 2018.
 *
 * The ontology passed in parameter of {@code repair} should only contain
 * assertion or subclass axioms.
 */
public class OntologyRepairWeakening extends OntologyRepair {
    /**
     * Possible strategies for computing the reference ontology.
     */
    public static enum RefOntologyStrategy {
        /**
         * Compute all maximal consistent subsets and select one at random.
         */
        RANDOM_MCS,
        /**
         * Compute some (but not necessarily all) maximal consistent subsets and select
         * one at random.
         */
        SOME_MCS,
        /**
         * Compute one maximal consistent subsets and select it.
         */
        ONE_MCS,
        /**
         * Compute the largest maximal consistent subsets and select one at random.
         */
        LARGEST_MCS,
        /**
         * Compute all maximal consistent subsets and select the intersection of them.
         */
        INTERSECTION_OF_MCS,
        /**
         * Compute some (but not necessarily all) maximal consistent subsets and select
         * the intersection of them.
         */
        INTERSECTION_OF_SOME_MCS,
    }

    /**
     * Possible strategies for computing bad axioms.
     */
    public static enum BadAxiomStrategy {
        /**
         * Select any random refutable axiom in the ontology.
         */
        RANDOM,
        /**
         * Select any random axiom that is not in some maximal consistent subsets.
         */
        NOT_IN_SOME_MCS,
        /**
         * Select any random axiom that is not in the largest maximal consistent
         * subsets.
         */
        NOT_IN_LARGEST_MCS,
        /**
         * Select the axiom that is in the least maximal consistent subsets.
         */
        IN_LEAST_MCS,
        /**
         * Select any random axiom that is in some minimal unsatisfiable subsets.
         */
        IN_SOME_MUS,
        /**
         * Select any random axiom that is in some minimal unsatisfiable subset.
         */
        IN_ONE_MUS,
        /**
         * Select any random axiom that is in the most minimal unsatisfiable subset.
         */
        IN_MOST_MUS,
        /**
         * Select any random axiom that is not in some maximal consistent subset.
         */
        NOT_IN_ONE_MCS
    }

    private RefOntologyStrategy refOntologySource;
    private BadAxiomStrategy badAxiomSource;
    private int weakeningFlags;

    /**
     * @param isRepaired
     *            The monotone predicate testing whether an ontology is repaired.
     * @param refOntologySource
     *            The strategy for computing the reference ontology.
     * @param badAxiomSource
     *            The strategy for computing bad axioms.
     * @param weakeningFlags
     *            The flags to use for weakening
     */
    public OntologyRepairWeakening(Predicate<Ontology> isRepaired, RefOntologyStrategy refOntologySource,
            BadAxiomStrategy badAxiomSource, int weakeningFlags) {
        super(isRepaired);
        this.refOntologySource = refOntologySource;
        this.badAxiomSource = badAxiomSource;
        this.weakeningFlags = weakeningFlags;
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing whether an ontology is repaired.
     */
    public OntologyRepairWeakening(Predicate<Ontology> isRepaired) {
        this(isRepaired, RefOntologyStrategy.ONE_MCS, BadAxiomStrategy.IN_SOME_MUS, AxiomWeakener.FLAG_DEFAULT);
    }

    /**
     * @return An instance of {@code OntologyRepairWeakening} that tries to make the
     *         ontology consistent.
     */
    public static OntologyRepair forConsistency() {
        return new OntologyRepairWeakening(Ontology::isConsistent);
    }

    /**
     * @return An instance of {@code OntologyRepairWeakening} that tries to make the
     *         ontology coherent.
     */
    public static OntologyRepair forCoherence() {
        return new OntologyRepairWeakening(Ontology::isCoherent);
    }

    /**
     * @param axioms
     *            The axioms that must not be entailed by the repaired ontology.
     * @return An instance of {@code OntologyRepairWeakening} that tries to remove
     *         all {@code axioms} from being entailed by the ontology.
     */
    public static OntologyRepair forRemovingEntailments(Collection<? extends OWLAxiom> axioms) {
        return new OntologyRepairWeakening(o -> axioms.stream().allMatch(axiom -> !o.isEntailed(axiom)));
    }

    /**
     * @param concept
     *            The concept that must be satisfiable in the repaired ontology.
     * @return An instance of {@code OntologyRepairWeakening} that tries to make
     *         {@code concept} satisfiable.
     */
    public static OntologyRepair forConceptSatisfiability(OWLClassExpression concept) {
        return new OntologyRepairWeakening(o -> o.isSatisfiable(concept));
    }

    /**
     * @param refOntology
     *            The reference ontology.
     * @param fullOntology
     *            The full ontology.
     * @return Weakener using the configuration of this repair.
     */
    public AxiomWeakener getWeakener(Ontology refOntology, Ontology fullOntology) {
        return new AxiomWeakener(refOntology, fullOntology, weakeningFlags);
    }

    /**
     * @param ontology
     *            The ontology to find a reference ontology for.
     * @return The set of axioms to include in the reference ontology to use for
     *         repairs.
     */
    public Stream<Set<OWLAxiom>> getRefAxioms(Ontology ontology) {
        switch (refOntologySource) {
            case INTERSECTION_OF_MCS: {
                return Stream.of(mcsPeekInfo(false, ontology.maximalConsistentSubsets(isRepaired)).reduce((a, b) -> {
                    a.removeIf(axiom -> !b.contains(axiom));
                    return a;
                }).get());
            }
            case INTERSECTION_OF_SOME_MCS: {
                return Stream
                        .of(mcsPeekInfo(false, ontology.someMaximalConsistentSubsets(isRepaired)).reduce((a, b) -> {
                            a.removeIf(axiom -> !b.contains(axiom));
                            return a;
                        }).get());
            }
            case LARGEST_MCS:
                return mcsPeekInfo(false, ontology.largestMaximalConsistentSubsets(isRepaired));
            case RANDOM_MCS:
                return mcsPeekInfo(false, ontology.maximalConsistentSubsets(isRepaired));
            case SOME_MCS:
                return mcsPeekInfo(false, ontology.someMaximalConsistentSubsets(isRepaired));
            case ONE_MCS: {
                var mcs = ontology.maximalConsistentSubset(isRepaired);
                if (mcs == null) {
                    return Stream.of();
                } else {
                    return Stream.of(mcs);
                }
            }
            default:
                throw new IllegalArgumentException("Unimplemented reference ontology choice strategy.");
        }
    }

    private Stream<OWLAxiom> mostFrequentIn(Stream<Set<OWLAxiom>> sets) {
        var occurrences = sets
                .flatMap(set -> set.stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        var max = occurrences.values().stream().max(Long::compareTo);
        return occurrences.entrySet().stream()
                .filter(entry -> entry.getValue() == max.get())
                .map(entry -> entry.getKey());
    }

    /**
     * @param ontology
     *            The ontology to find bad axioms in.
     * @return The stream of axioms between which to select the next axiom to
     *         weaken.
     */
    public Stream<OWLAxiom> findBadAxioms(Ontology ontology) {
        switch (badAxiomSource) {
            case IN_LEAST_MCS: {
                return mostFrequentIn(ontology.minimalCorrectionSubsets(isRepaired));
            }
            case NOT_IN_LARGEST_MCS: {
                return mostFrequentIn(ontology.smallestMinimalCorrectionSubsets(isRepaired));
            }
            case NOT_IN_SOME_MCS:
                return mostFrequentIn(ontology.someMinimalCorrectionSubsets(isRepaired));
            case IN_SOME_MUS:
                return mostFrequentIn(ontology.someMinimalUnsatisfiableSubsets(isRepaired));
            case IN_MOST_MUS:
                return mostFrequentIn(ontology.minimalUnsatisfiableSubsets(isRepaired));
            case IN_ONE_MUS: {
                var mus = ontology.minimalUnsatisfiableSubset(isRepaired);
                if (mus == null) {
                    return Stream.of();
                } else {
                    return mus.stream();
                }
            }
            case NOT_IN_ONE_MCS: {
                var mcs = ontology.minimalCorrectionSubset(isRepaired);
                if (mcs == null) {
                    return Stream.of();
                } else {
                    return mcs.stream();
                }
            }
            case RANDOM:
                return ontology.refutableAxioms();
            default:
                throw new IllegalArgumentException("Unimplemented bad axiom choice strategy.");
        }
    }

    @Override
    public void repair(Ontology ontology) {
        var refAxioms = Utils.randomChoice(getRefAxioms(ontology));
        infoMessage("Selected a reference ontology with " + refAxioms.size() + " axioms.");
        try (var refOntology = ontology.cloneWithRefutable(refAxioms).withSeparateCache()) {
            var axiomWeakener = getWeakener(refOntology, ontology);
            while (!isRepaired(ontology)) {
                var badAxioms = Utils.toList(findBadAxioms(ontology));
                infoMessage("Found " + badAxioms.size() + " possible bad axioms.");
                var badAxiom = Utils.randomChoice(badAxioms);
                infoMessage("Selected the bad axiom " + Utils.prettyPrintAxiom(badAxiom) + ".");
                var weakerAxioms = Utils.toList(axiomWeakener.weakerAxioms(badAxiom));
                infoMessage("Found " + weakerAxioms.size() + " weaker axioms.");
                var weakerAxiom = Utils.randomChoice(weakerAxioms);
                infoMessage("Selected the weaker axiom " + Utils.prettyPrintAxiom(weakerAxiom) + ".");
                ontology.replaceAxiom(badAxiom, weakerAxiom);
            }
        }
    }

    @Override
    public Stream<Ontology> multiple(Ontology ontology) {
        // Optimized version that reuses the cached reasoners and axiom weakeners.
        var weakeners = new HashMap<Set<OWLAxiom>, AxiomWeakener>();
        var refOntologyBase = ontology.cloneWithSeparateCache();
        return Stream.generate(() -> {
            try {
                var refAxioms = Utils.randomChoice(getRefAxioms(ontology));
                AxiomWeakener axiomWeakener;
                synchronized (weakeners) {
                    axiomWeakener = weakeners.computeIfAbsent(refAxioms,
                            ax -> getWeakener(refOntologyBase.cloneWithRefutable(ax), ontology));
                }
                var copy = ontology.clone();
                while (!isRepaired(copy)) {
                    var badAxioms = Utils.toList(findBadAxioms(copy));
                    infoMessage("Found " + badAxioms.size() + " possible bad axioms.");
                    var badAxiom = Utils.randomChoice(badAxioms);
                    infoMessage("Selected the bad axiom " + Utils.prettyPrintAxiom(badAxiom) + ".");
                    var weakerAxioms = Utils.toList(axiomWeakener.weakerAxioms(badAxiom));
                    infoMessage("Found " + weakerAxioms.size() + " weaker axioms.");
                    var weakerAxiom = Utils.randomChoice(weakerAxioms);
                    infoMessage("Selected the weaker axiom " + Utils.prettyPrintAxiom(weakerAxiom) + ".");
                    copy.replaceAxiom(badAxiom, weakerAxiom);
                }
                infoMessage("Found repair.");
                return copy;
            } catch (OutOfMemoryError e) {
                weakeners.clear();
                return null;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }).filter(onto -> onto != null).onClose(() -> {
            refOntologyBase.closeAll();
        });
    }
}
