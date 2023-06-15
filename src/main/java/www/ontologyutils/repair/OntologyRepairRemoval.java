package www.ontologyutils.repair;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import org.semanticweb.owlapi.model.*;

import www.ontologyutils.toolbox.*;

/**
 * The ontology is repaired by removing axioms.
 */
public class OntologyRepairRemoval extends OntologyRepair {
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

    private BadAxiomStrategy badAxiomSource;

    /**
     * @param isRepaired
     *            The monotone predicate testing whether an ontology is repaired.
     * @param badAxiomSource
     *            The strategy for computing bad axioms.
     */
    public OntologyRepairRemoval(Predicate<Ontology> isRepaired, BadAxiomStrategy badAxiomSource) {
        super(isRepaired);
        this.badAxiomSource = badAxiomSource;
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing whether an ontology is repaired.
     */
    public OntologyRepairRemoval(Predicate<Ontology> isRepaired) {
        this(isRepaired, BadAxiomStrategy.IN_SOME_MUS);
    }

    /**
     * @return An instance of {@code OntologyRepairRemoval} that tries to make the
     *         ontology consistent.
     */
    public static OntologyRepair forConsistency() {
        return new OntologyRepairRemoval(Ontology::isConsistent);
    }

    /**
     * @return An instance of {@code OntologyRepairRemoval} that tries to make the
     *         ontology coherent.
     */
    public static OntologyRepair forCoherence() {
        return new OntologyRepairRemoval(Ontology::isCoherent);
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
        while (!isRepaired(ontology)) {
            var badAxioms = Utils.toList(findBadAxioms(ontology));
            infoMessage("Found " + badAxioms.size() + " possible bad axioms.");
            var badAxiom = Utils.randomChoice(badAxioms);
            infoMessage("Selected the bad axiom " + Utils.prettyPrintAxiom(badAxiom) + ".");
            ontology.removeAxioms(badAxiom);
        }
    }
}
