package www.ontologyutils.repair;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.toolbox.*;

/**
 * An implementation of {@code OntologyRepair}. It repairs an inconsistent
 * ontology by choosing the best repair found using monte carlo tree search.
 * For efficiency, all repairs are performed using the same reference ontology.
 */
public class OntologyRepairMctsWeakening extends OntologyRepairBestOfKWeakening {
    private static class GameMove {
        public boolean select;
        public OWLAxiom axiom;

        public GameMove(boolean select, OWLAxiom axiom) {
            this.select = select;
            this.axiom = axiom;
        }

        @Override
        public int hashCode() {
            return Objects.hash(select, axiom);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (!(obj instanceof GameMove)) {
                return false;
            } else {
                GameMove other = (GameMove) obj;
                return select == other.select && Objects.equals(axiom, other.axiom);
            }
        }
    }

    private class Game implements Mcts.Game<GameMove> {
        private Ontology ontology;
        private AxiomWeakener weakener;
        private OWLAxiom toWeaken;
        private Function<Ontology, Double> quality;

        public Game(Ontology ontology, AxiomWeakener weakener, Function<Ontology, Double> quality) {
            this.ontology = ontology.cloneWithSeparateCache();
            this.weakener = weakener;
            this.quality = quality;
            this.toWeaken = null;
        }

        @Override
        public Mcts.Game<GameMove> copy() {
            var copy = new Game(ontology, weakener, quality);
            copy.toWeaken = toWeaken;
            return copy;
        }

        @Override
        public Stream<GameMove> possibleMoves() {
            if (toWeaken == null) {
                return findBadAxioms(ontology).map(ax -> new GameMove(true, ax));
            } else {
                return weakener.weakerAxioms(toWeaken).map(ax -> new GameMove(false, ax));
            }
        }

        @Override
        public void performMove(GameMove move) {
            if (toWeaken == null) {
                assert move.select;
                toWeaken = move.axiom;
            } else {
                assert !move.select;
                ontology.replaceAxiom(toWeaken, move.axiom);
                toWeaken = null;
            }
        }

        @Override
        public boolean isTerminal() {
            return isRepaired(ontology);
        }

        @Override
        public double terminalValue() {
            return quality.apply(ontology);
        }

        @Override
        public void close() {
            ontology.close();
        }
    }

    /**
     * @param isRepaired
     *            The predicate testing whether an ontology is repaired.
     * @param refOntologySource
     *            The strategy for computing the reference ontology.
     * @param badAxiomSource
     *            The strategy for computing bad axioms.
     * @param quality
     *            Function for evaluating the quality of a repair.
     * @param numberOfRounds
     *            The number of repairs to perform.
     */
    public OntologyRepairMctsWeakening(Predicate<Ontology> isRepaired, RefOntologyStrategy refOntologySource,
            BadAxiomStrategy badAxiomSource, Function<Ontology, Double> quality, int numberOfRounds) {
        super(isRepaired, refOntologySource, badAxiomSource, quality, numberOfRounds);
    }

    /**
     * @param isRepaired
     *            The predicate testing whether an ontology is repaired.
     * @param numberOfRounds
     *            The number of repairs to perform.
     */
    public OntologyRepairMctsWeakening(Predicate<Ontology> isRepaired, int numberOfRounds) {
        this(isRepaired, RefOntologyStrategy.INTERSECTION_OF_SOME_MCS, BadAxiomStrategy.IN_ONE_MUS,
                o -> (double) o.inferredTaxonomyAxioms().count(), numberOfRounds);
    }

    /**
     * @param numberOfRounds
     *            The number of repairs to perform.
     * @return An instance of {@code OntologyRepairBestOfKWeakening} that tries to
     *         make the ontology consistent.
     */
    public static OntologyRepair forConsistency(int numberOfRounds) {
        return new OntologyRepairMctsWeakening(Ontology::isConsistent, numberOfRounds);
    }

    /**
     * @param numberOfRounds
     *            The number of repairs to perform.
     * @return An instance of {@code OntologyRepairBestOfKWeakening} that tries to
     *         make the ontology coherent.
     */
    public static OntologyRepair forCoherence(int numberOfRounds) {
        return new OntologyRepairMctsWeakening(Ontology::isCoherent, numberOfRounds);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void repair(Ontology ontology) {
        var refAxioms = Utils.randomChoice(getRefAxioms(ontology));
        infoMessage("Selected a reference ontology with " + refAxioms.size() + " axioms.");
        var bestAxioms = new Set<?>[] { Set.of() };
        var bestQuality = new Double[] { Double.NEGATIVE_INFINITY };
        try (var refOntology = ontology.cloneWithRefutable(refAxioms).withSeparateCache()) {
            var axiomWeakener = new AxiomWeakener(refOntology, ontology);
            var game = new Game(ontology, axiomWeakener, onto -> {
                var thisQuality = quality.apply(onto);
                synchronized (bestQuality) {
                    if (thisQuality > bestQuality[0]) {
                        bestAxioms[0] = Utils.toSet(onto.axioms());
                        bestQuality[0] = thisQuality;
                    }
                }
                return thisQuality;
            });
            try (var mcts = new Mcts<>(game)) {
                var parallelism = Runtime.getRuntime().availableProcessors();
                var executor = Executors.newFixedThreadPool(parallelism);
                for (int i = 0; i < numberOfRounds; i++) {
                    executor.execute(() -> {
                        mcts.runSimulation();
                    });
                }
                executor.shutdown();
                try {
                    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    throw new CanceledException();
                }
            }
        }
        ontology.setRefutableAxioms((Set<OWLAxiom>) bestAxioms[0]);
    }
}
