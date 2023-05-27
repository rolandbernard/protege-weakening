package www.ontologyutils.repair;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.*;

import www.ontologyutils.toolbox.*;

/**
 * An ontology is repaired if {@code isRepaired} returns true. The predicate
 * must be such that if an ontology satisfies the predicate, every ontology with
 * fewer consequences will also satisfy the predicate, i.e. it must be monotone.
 */
public abstract class OntologyRepair implements OntologyModification {
    /**
     * Monotone predicate that tests if an ontology is repaired.
     */
    protected Predicate<Ontology> isRepaired;
    /**
     * A callback that should be called if we have info messages.
     */
    protected Consumer<String> infoCallback;

    /**
     * @param isRepaired
     *            The condition by which the repair is evaluated.
     */
    protected OntologyRepair(Predicate<Ontology> isRepaired) {
        this.isRepaired = isRepaired;
    }

    /**
     * Applies the repair to the given ontology. After this method returns, the
     * ontology must be repaired.
     *
     * @param ontology
     *            The ontology to repair.
     */
    public abstract void repair(Ontology ontology);

    /**
     * Simple utility function applying the internal predicate.
     *
     * @param ontology
     *            The ontology to check.
     * @return True iff the ontology is repaired.
     */
    public boolean isRepaired(Ontology ontology) {
        return isRepaired.test(ontology);
    }

    /**
     * @param infoCallback
     *            The callback to call when we have a new progress message.
     */
    public void setInfoCallback(Consumer<String> infoCallback) {
        this.infoCallback = infoCallback;
    }

    /**
     * @param message
     *            The new info message to send.
     */
    protected synchronized void infoMessage(String message) {
        if (infoCallback != null) {
            infoCallback.accept(message);
        }
    }

    /**
     * @param minimal
     *            Whether we found a minimal or maximal subset.
     * @param stream
     *            The stream containing the subsets for which to create info
     *            messages.
     * @return A stream equivalent to {@code stream}.
     */
    protected Stream<Set<OWLAxiom>> mcsPeekInfo(boolean minimal, Stream<Set<OWLAxiom>> stream) {
        return stream.peek(mcs -> infoMessage("Found " + (minimal ? "minimal correction" : "maximal consistent")
                + " subset of size " + mcs.size() + "."));
    }

    @Override
    public void apply(Ontology ontology) throws IllegalArgumentException {
        infoMessage("Checking precondition...");
        if (isRepaired(ontology)) {
            infoMessage("The ontology is already repaired.");
            return;
        }
        try (var nonRefutable = ontology.clone()) {
            nonRefutable.removeAxioms(ontology.refutableAxioms());
            if (!isRepaired(nonRefutable)) {
                throw new IllegalArgumentException("The ontology is not reparable.");
            }
        }
        infoMessage("Starting the repair...");
        repair(ontology);
        infoMessage("Finished repairing the ontology.");
    }
}
