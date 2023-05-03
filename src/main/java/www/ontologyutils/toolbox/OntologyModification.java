package www.ontologyutils.toolbox;

/**
 * Interface to be implemented by algorithms that modify the ontology, e.g.,
 * by ontology repair and normalization.
 */
public interface OntologyModification {
    /**
     * Applies the modification represented by this object to the given ontology.
     *
     * @param ontology
     *            e ontology to modify
     */
    public void apply(Ontology ontology);

    /**
     * Return a new ontology that has this modification applied to it.
     *
     * @param ontology
     *            The ontology to modify
     * @return The modified ontology
     */
    default public Ontology modified(Ontology ontology) {
        var copy = ontology.clone();
        apply(copy);
        return copy;
    }
}
