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
        ALL_MCS, SOME_MCS, ONE_MCS,
    }

    private McsComputationStrategy mcsComputation;

    public OntologyRepairRandomMcs(Predicate<Ontology> isRepaired, McsComputationStrategy mcs) {
        super(isRepaired);
        this.mcsComputation = mcs;
    }

    public OntologyRepairRandomMcs(Predicate<Ontology> isRepaired) {
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
     * @return An instance of {@code OntologyRepairRandomMcs} that tries to make the
     *         ontology coherent.
     */
    public static OntologyRepair forCoherence() {
        return new OntologyRepairRandomMcs(Ontology::isCoherent);
    }

    /**
     * @return An instance of {@code OntologyRepairRandomMcs} that tries to remove
     *         all {@code axioms} from being entailed by the ontology.
     */
    public static OntologyRepair forRemovingEntailments(Collection<? extends OWLAxiom> axioms) {
        return new OntologyRepairRandomMcs(o -> axioms.stream().allMatch(axiom -> !o.isEntailed(axiom)));
    }

    /**
     * @return An instance of {@code OntologyRepairRandomMcs} that tries to make
     *         {@code concept} satisfiable.
     */
    public static OntologyRepair forConceptSatisfiability(OWLClassExpression concept) {
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
    public Stream<Set<OWLAxiom>> computeMcs(Ontology ontology) {
        switch (mcsComputation) {
            case ALL_MCS:
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
    public void repair(Ontology ontology) {
        var toRemove = Utils.randomChoice(mcsPeekInfo(true, computeMcs(ontology)));
        ontology.removeAxioms(toRemove);
        infoMessage("Selected a repair with " + ontology.axioms().count() + " axioms.");
    }
}
