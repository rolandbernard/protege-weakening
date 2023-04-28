package www.ontologyutils.repair;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.*;

import www.ontologyutils.toolbox.*;

/**
 * A simple implementation of {@code OntologyRepair}. It repairs an inconsistent
 * ontology into an ontology made of a randomly chosen maximally consistent set
 * of axioms in the input ontology.
 */
public class OntologyRepairRandomMcs extends OntologyRepair {
    public static enum McsComputationStrategy {
        All_MCS, SOME_MCS, ONE_MCS,
    }

    private final McsComputationStrategy mcsComputation;

    public OntologyRepairRandomMcs(final Predicate<Ontology> isRepaired, final McsComputationStrategy mcs) {
        super(isRepaired);
        this.mcsComputation = mcs;
    }

    public OntologyRepairRandomMcs(final Predicate<Ontology> isRepaired) {
        this(isRepaired, McsComputationStrategy.SOME_MCS);
    }

    /**
     * @return An instance of {@code OntologyRepairRandomMcs} that tries to make the
     *         ontology consistent.
     */
    public static OntologyRepair forConsistency() {
        return new OntologyRepairRandomMcs(Ontology::isConsistent);
    }

    /**
     * @return An instance of {@code OntologyRepairRandomMcs} that tries to remove
     *         all {@code axioms} from being entailed by the ontology.
     */
    public static OntologyRepair forRemovingEntailments(final Collection<? extends OWLAxiom> axioms) {
        return new OntologyRepairRandomMcs(o -> axioms.stream().allMatch(axiom -> !o.isEntailed(axiom)));
    }

    /**
     * @return An instance of {@code OntologyRepairRandomMcs} that tries to make
     *         {@code concept} satisfiable.
     */
    public static OntologyRepair forConceptSatisfiability(final OWLClassExpression concept) {
        return new OntologyRepairRandomMcs(o -> o.isSatisfiable(concept));
    }

    /**
     * Compute some or all minimal correction subsets of the given ontology
     * depending on the parameters of this object.
     *
     * @param ontology
     *            The ontology for which to compute the minimal correction subset
     * @return A stream containing minimal correction subsets
     */
    public Stream<Set<OWLAxiom>> computeMcs(final Ontology ontology) {
        switch (mcsComputation) {
            case All_MCS:
                return ontology.minimalCorrectionSubsets(isRepaired);
            case SOME_MCS:
                return ontology.someMinimalCorrectionSubsets(isRepaired);
            case ONE_MCS:
                return Stream.of(ontology.minimalCorrectionSubset(isRepaired));
            default:
                throw new IllegalArgumentException("Unimplemented maximal consistent subset computation method.");
        }
    }

    @Override
    public void repair(final Ontology ontology) {
        final var toRemove = Utils.randomChoice(computeMcs(ontology));
        ontology.removeAxioms(toRemove);
    }
}
