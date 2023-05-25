package www.ontologyutils.repair;

import java.util.*;
import java.util.function.*;

import www.ontologyutils.toolbox.*;

/**
 * An implementation of {@code OntologyRepair}. It repairs an inconsistent
 * ontology by choosing the best maximal consistent subset according to some
 * quality measure.
 */
public class OntologyRepairBestMcs extends OntologyRepairRandomMcs {
    private Function<Ontology, Double> quality;

    /**
     * @param isRepaired
     *            The predicate testing whether an ontology is repaired.
     * @param mcs
     *            The strategy for computing maximal consistent subsets.
     * @param quality
     *            Function for evaluating the quality of a repair.
     */
    public OntologyRepairBestMcs(Predicate<Ontology> isRepaired, McsComputationStrategy mcs,
            Function<Ontology, Double> quality) {
        super(isRepaired, mcs);
        this.quality = quality;
    }

    /**
     * @param isRepaired
     *            The predicate testing whether an ontology is repaired.
     */
    public OntologyRepairBestMcs(Predicate<Ontology> isRepaired) {
        this(isRepaired, McsComputationStrategy.SOME_MCS, o -> (double) o.inferredTaxonomyAxioms().count());
    }

    /**
     * @return An instance of {@code OntologyRepairBestMcs} that tries to make the
     *         ontology consistent.
     */
    public static OntologyRepair forConsistency() {
        return new OntologyRepairBestMcs(Ontology::isConsistent);
    }

    /**
     * @return An instance of {@code OntologyRepairBestMcs} that tries to make the
     *         ontology coherent.
     */
    public static OntologyRepair forCoherence() {
        return new OntologyRepairBestMcs(isCoherent());
    }

    @Override
    public void repair(Ontology ontology) {
        var possibleCorrections = Utils.toList(mcsPeekInfo(true, computeMcs(ontology)));
        var bestCorrection = possibleCorrections.parallelStream()
                .map(correction -> {
                    try (var copy = ontology.cloneWithSeparateCache()) {
                        copy.removeAxioms(correction);
                        return new AbstractMap.SimpleEntry<>(correction, quality.apply(copy));
                    }
                })
                .max(Comparator.comparingDouble(e -> e.getValue()))
                .get().getKey();
        ontology.removeAxioms(bestCorrection);
        infoMessage("Selected a repair with " + ontology.axioms().count() + " axioms.");
    }
}
