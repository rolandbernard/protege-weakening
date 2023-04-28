package www.ontologyutils.toolbox;

import java.util.*;
import java.util.stream.*;

import org.semanticweb.owlapi.model.*;

/**
 * Utility class that contains static constants and methods that don't neatly
 * fit anywhere else.
 */
public final class Utils {
    private static final ThreadLocal<Random> random = new ThreadLocal<>() {
        protected Random initialValue() {
            return new Random();
        };
    };

    /**
     * Prevents instantiation.
     */
    private Utils() {
    }

    /**
     * Used for exceptions that we don't want to or can't handle. This function is
     * guaranteed to never return.
     *
     * @param e
     *            The exception that caused the panic.
     * @return Return a runtime exception it is given, so that it can be thrown
     *         again to
     *         avoid control flow checks failing.
     */
    public static RuntimeException panic(final Exception e) {
        e.printStackTrace();
        System.exit(1);
        return new RuntimeException(e);
    }

    /**
     * Set the seed for the currently used random instance to {@code seed}.
     *
     * @param seed
     */
    public static void randomSeed(final long seed) {
        random.get().setSeed(seed);
    }

    /**
     * Select a random from a finite stream uniformly at random.
     *
     * @param <T>
     * @param stream
     * @return A random element in {@code stream}.
     */
    public static <T> T randomChoice(final Stream<? extends T> stream) {
        final var flatList = stream.collect(Collectors.toList());
        final int randomIdx = random.get().nextInt(flatList.size());
        return flatList.get(randomIdx);
    }

    /**
     * Select a random element for the collection uniformly ar random.
     *
     * @param <T>
     * @param collection
     * @return A random element in {@code collection}.
     */
    public static <T> T randomChoice(final Collection<? extends T> collection) {
        return randomChoice(collection.stream());
    }

    /**
     * Return a random order from the finite stream.
     *
     * @param <T>
     * @param stream
     * @return A random order of {@code stream}.
     */
    public static <T> List<T> randomOrder(final Stream<? extends T> stream) {
        final var flatList = stream.map(t -> (T) t).collect(Collectors.toList());
        Collections.shuffle(flatList, random.get());
        return flatList;
    }

    /**
     * Return a random order if the collection.
     *
     * @param <T>
     * @param collection
     * @return A random order of {@code collection}.
     */
    public static <T> List<T> randomOrder(final Collection<? extends T> collection) {
        return randomOrder(collection.stream());
    }

    /**
     * @param <T>
     * @param list
     * @param idx
     * @param value
     * @return A stream that contains all elements in {@code list} but the one at
     *         {@code idx} which is replace by {@code value}.
     */
    public static <T> Stream<T> replaceInList(final List<T> list, final int idx, final T value) {
        return IntStream.range(0, list.size()).mapToObj(j -> j)
                .map(j -> idx == j ? value : list.get(j));
    }

    /**
     * @param <T>
     * @param list
     * @param idx
     * @return A stream that contains all elements in {@code list} except the one at
     *         {@code idx}.
     */
    public static <T> Stream<T> removeFromList(final List<T> list, final int idx) {
        return IntStream.range(0, list.size()).filter(j -> idx != j).mapToObj(list::get);
    }

    /**
     * Compute the power set of the given collection. The implementation here is
     * only able to handle up to 63 elements in {@code set}.
     *
     * @param <T>
     * @param set
     * @return The power set of {@code set}
     * @throws IllegalArgumentException
     *             If {@code set} contains more than 63
     *             elements.
     */
    public static <T> Stream<Set<T>> powerSet(final Collection<? extends T> set) throws IllegalArgumentException {
        if (set.size() > 63) {
            throw new IllegalArgumentException("Set is too large to compute the power set.");
        }
        final var flatList = List.copyOf(set);
        final int resultLength = 1 << flatList.size();
        return IntStream.range(0, resultLength).mapToObj(binarySet -> {
            final var subset = new HashSet<T>();
            for (int bit = 0; bit < flatList.size(); bit++) {
                if (((binarySet >> bit) & 1) != 0) {
                    subset.add(flatList.get(bit));
                }
            }
            return subset;
        });
    }

    /**
     * E.g., C1 = exists p. (A or B) is the same as C2 = exists p. (A or B), even if
     * the representing objects are different, that is they are the same even if
     * C1 != C2 or !C1.equals(C2). On the other hand, we say that A and B is the
     * same as B and A.
     *
     * @param c1
     * @param c2
     * @return true when {@code c1} and {@code c2} are the same concept at the
     *         syntactic level.
     */
    public static boolean sameConcept(final OWLClassExpression c1, final OWLClassExpression c2) {
        if (c1.equals(c2)) {
            return true;
        } else if (c1.getClassExpressionType() != c2.getClassExpressionType()) {
            return false;
        } else {
            switch (c1.getClassExpressionType()) {
                case OWL_CLASS: {
                    return c1.equals(c2);
                }
                case OBJECT_COMPLEMENT_OF: {
                    final var n1 = (OWLObjectComplementOf) c1;
                    final var n2 = (OWLObjectComplementOf) c2;
                    return sameConcept(n1.getOperand(), n2.getOperand());
                }
                case OBJECT_UNION_OF:
                case OBJECT_INTERSECTION_OF: {
                    final var u1 = (OWLNaryBooleanClassExpression) c1;
                    final var u2 = (OWLNaryBooleanClassExpression) c2;
                    return u1.getOperands().stream()
                            .allMatch(d1 -> u2.getOperands().stream().anyMatch(d2 -> sameConcept(d1, d2)));
                }
                case OBJECT_MIN_CARDINALITY:
                case OBJECT_MAX_CARDINALITY:
                case OBJECT_EXACT_CARDINALITY:
                case OBJECT_ALL_VALUES_FROM:
                case OBJECT_SOME_VALUES_FROM: {
                    final var q1 = (OWLQuantifiedObjectRestriction) c1;
                    final var q2 = (OWLQuantifiedObjectRestriction) c2;
                    return q1.getProperty().equals(q2.getProperty()) && sameConcept(q1.getFiller(), q2.getFiller());
                }
                default:
                    throw new IllegalArgumentException(
                            "Concept type " + c1.getClassExpressionType() + " not supported for comparison.");
            }
        }
    }

    /**
     * @param owlString
     * @return
     */
    public static String pretty(final String owlString) {
        return owlString.replaceAll("<http.*?#", "").replaceAll(">", "").replaceAll("<", "");
    }

    /**
     * @param ax
     * @return a pretty string representing {@code ax}, without its annotations and
     *         without namespaces.
     */
    public static String prettyPrintAxiom(final OWLAxiom ax) {
        return ax.getAxiomWithoutAnnotations().toString()
                .replaceAll("<http.*?#", "").replaceAll(">", "").replaceAll("<", "")
                .replaceFirst("Annotation(.*?) ", "");
    }

    /**
     * @param axioms
     * @return True iff {@code axioms} are consistent together.
     */
    public static boolean isConsistent(final Collection<? extends OWLAxiom> axioms) {
        try (final var ontology = Ontology.withAxioms(axioms)) {
            return ontology.isConsistent();
        }
    }
}
