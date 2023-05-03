package www.ontologyutils.repair;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.toolbox.*;

/**
 * An ontology is repaired if {@code isRepaired} returns true. The predicate
 * must be such that if an ontology satisfies the predicate, every ontology with
 * fewer consequences will also satisfy the predicate, i.e. it must be monotone.
 */
public abstract class OntologyRepair implements OntologyModification {
    protected Predicate<Ontology> isRepaired;
    protected Consumer<String> infoCallback;

    /**
     * @param isRepaired
     *            The condition by which the repair is evaluated.
     */
    protected OntologyRepair(Predicate<Ontology> isRepaired) {
        this.isRepaired = isRepaired;
    }

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

    public void setInfoCallback(Consumer<String> infoCallback) {
        this.infoCallback = infoCallback;
    }

    protected void infoMessage(String message) {
        if (infoCallback != null) {
            infoCallback.accept(message);
        }
    }

    protected Stream<Set<OWLAxiom>> mcsPeekInfo(boolean minimal, Stream<Set<OWLAxiom>> stream) {
        return stream.peek(mcs -> infoMessage("Found " + (minimal ? "minimal correction" : "maximal consistent")
                + " subset of size " + mcs.size() + "."));
    }

    @Override
    public void apply(Ontology ontology) throws IllegalArgumentException {
        infoMessage("Checking precondition...");
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
