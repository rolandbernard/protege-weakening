package www.ontologyutils.toolbox;

import java.util.*;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

/**
 * Utility class for creating fresh atom equivalent to some other atom, creating
 * the required axiom for asserting equivalence.
 */
public class FreshAtoms {
    private static Set<OWLAxiom> freshAtomsEquivalenceAxioms = new HashSet<>();

    /**
     * @return the set of equivalence axioms resulting from the creation of fresh
     *         atoms.
     */
    public static Set<OWLAxiom> getFreshAtomsEquivalenceAxioms() {
        return freshAtomsEquivalenceAxioms;
    }

    /**
     * Empties the set of equivalence axioms resulting from the creation of fresh
     * atoms.
     */
    public static void resetFreshAtomsEquivalenceAxioms() {
        freshAtomsEquivalenceAxioms = new HashSet<>();
    }

    /**
     * @param e
     *            The class expression for the new atom.
     * @return a fresh {@code OWLClassExpression} with name "#FRESH[string
     *         representing {@code e}]"
     */
    public static OWLClassExpression createFreshAtomCopy(OWLClassExpression e) {
        return createFreshAtomCopy(e, "FRESH");
    }

    /**
     * @param e
     *            The class expression for the new axiom
     * @param tag
     *            A tag to prepend to the IRI of the new atom.
     * @return a fresh {@code OWLClassExpression} with name "#tag[string
     *         representing {@code e}]"
     */
    public static OWLClassExpression createFreshAtomCopy(OWLClassExpression e, String tag) {
        OWLDataFactory dataFactory = OWLManager.getOWLDataFactory();
        String freshName = "#[" + e + "]";
        OWLClassExpression fresh = dataFactory.getOWLEntity(EntityType.CLASS, IRI.create(tag + freshName));
        Collection<OWLClassExpression> equiv = new ArrayList<>();
        equiv.add(e);
        equiv.add(fresh);
        freshAtomsEquivalenceAxioms.add(dataFactory.getOWLEquivalentClassesAxiom(Set.copyOf(equiv)));
        return fresh;
    }
}
