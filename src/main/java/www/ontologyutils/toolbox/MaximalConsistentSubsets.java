package www.ontologyutils.toolbox;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.*;

import org.semanticweb.owlapi.model.*;

/**
 * This an adaptation of the algorithm in Robert Malouf's "Maximal Consistent
 * Subsets", Computational Linguistics, vol 33(2), p.153-160, 2007.
 */
public class MaximalConsistentSubsets {
    private static class QueueItem {
        public int k;
        public Set<OWLAxiom> removed;

        public QueueItem(int k, Set<OWLAxiom> removed) {
            this.k = k;
            this.removed = removed;
        }
    }

    private Predicate<Ontology> isRepaired;
    private Ontology ontology;
    private List<OWLAxiom> axioms;
    private Deque<QueueItem> queue;
    private SetOfSets<OWLAxiom> results;
    private boolean largest;
    private Set<OWLAxiom> lastResult;
    private Set<OWLAxiom> result;

    /**
     * @param ontology
     *            The ontology for which to compute the maximal consistent
     *            subsets.
     * @param isRepaired
     *            The predicate with which to measure "consistency".
     * @param largest
     *            Return only the largest maximal consistent sets (or
     *            smallest corrections)
     */
    public MaximalConsistentSubsets(Ontology ontology, Predicate<Ontology> isRepaired,
            boolean largest) {
        this.ontology = ontology;
        this.isRepaired = isRepaired;
        axioms = ontology.refutableAxioms().collect(Collectors.toList());
        queue = new ArrayDeque<>();
        queue.add(new QueueItem(0, new HashSet<>()));
        results = new SetOfSets<>();
        this.largest = largest;
    }

    /**
     * @param ontology
     *            The ontology for which to compute the maximal consistent
     *            subsets.
     * @param isRepaired
     *            The predicate with which to measure "consistency".
     */
    public MaximalConsistentSubsets(Ontology ontology, Predicate<Ontology> isRepaired) {
        this(ontology, isRepaired, false);
    }

    /**
     * @param ontology
     *            The ontology for which to compute the maximal consistent
     *            subsets.
     * @throws IllegalArgumentException
     *             If there is no maximal consistent subset.
     */
    public MaximalConsistentSubsets(Ontology ontology) throws IllegalArgumentException {
        this(ontology, Ontology::isConsistent);
    }

    /**
     * Tests whether adding any of the axioms in {@code axioms} to {@code ontology}
     * make the ontology inconsistent.
     *
     * @param ontology
     * @param axioms
     * @return True iff {@code ontology} is maximally consistent with respect to
     *         {@code axioms}.
     */
    public static boolean isMaximallyConsistentWithRespectTo(Ontology ontology,
            Collection<? extends OWLAxiom> axioms) {
        if (!ontology.isConsistent()) {
            return false;
        }
        var contained = ontology.axioms().collect(Collectors.toSet());
        try (var copy = ontology.clone()) {
            for (var axiom : axioms) {
                if (!contained.contains(axiom)) {
                    copy.addAxioms(axiom);
                    if (copy.isConsistent()) {
                        return false;
                    }
                    copy.removeAxioms(axiom);
                }
            }
            return true;
        }
    }

    /**
     * Compute the next result.
     *
     * @return True if a new result was found, false otherwise.
     */
    private boolean computeNextResult() {
        while (!queue.isEmpty()) {
            var current = queue.pop();
            if (largest && lastResult != null && current.removed.size() > lastResult.size()) {
                break;
            } else if (results.containsSubset(current.removed)) {
                continue;
            } else {
                try (var subset = ontology.clone()) {
                    subset.removeAxioms(current.removed);
                    if (isRepaired.test(subset)) {
                        results.add(current.removed);
                        result = current.removed;
                        lastResult = result;
                        return true;
                    } else {
                        subset.removeAxioms(axioms.stream().skip(current.k));
                        for (int i = current.k; i < axioms.size(); i++) {
                            var removed = new HashSet<>(current.removed);
                            var axiom = axioms.get(i);
                            removed.add(axiom);
                            queue.add(new QueueItem(i + 1, removed));
                            subset.addAxioms(axiom);
                            if (i < axioms.size() - 1 && !isRepaired.test(subset)) {
                                break;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * @return A stream producing all maximal consistent sets.
     */
    public Stream<Set<OWLAxiom>> stream() {
        return correctionStream().map(ontology::complement);
    }

    /**
     * @return A stream producing all complements of maximal consistent subsets
     *         (i.e. minimal correction subsets).
     */
    public Stream<Set<OWLAxiom>> correctionStream() {
        return Stream.concat(results.stream(),
                StreamSupport.stream(Spliterators.spliteratorUnknownSize(new Iterator<Set<OWLAxiom>>() {
                    public boolean hasNext() {
                        if (result == null) {
                            computeNextResult();
                        }
                        return result != null;
                    }

                    @Override
                    public Set<OWLAxiom> next() {
                        if (result == null) {
                            computeNextResult();
                        }
                        var next = result;
                        result = null;
                        return next;
                    }
                }, Spliterator.NONNULL), false));
    }
}
