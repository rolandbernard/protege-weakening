package www.ontologyutils.refinement;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.*;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import www.ontologyutils.toolbox.*;

/**
 * Implements the upward and downward cover operations. This object must be
 * closed after use to free up all resources associated with the internal
 * {@code OWLReasoner} and {@code OWLOntology}.
 *
 * The implementation is based on the approach presented in Troquard, Nicolas,
 * et al. "Repairing ontologies via axiom weakening." Proceedings of the AAAI
 * Conference on Artificial Intelligence. Vol. 32. No. 1. 2018. Definition 3.
 *
 * The implementation of role and number covers is based on the approach
 * presented in Confalonieri, R., Galliani, P., Kutz, O., Porello, D., Righetti,
 * G., &amp; Toquard, N. (2020). Towards even more irresistible axiom weakening.
 */
public class Covers implements AutoCloseable {
    public static class Cover {
        private Function<OWLClassExpression, Stream<OWLClassExpression>> conceptCover;
        private Function<OWLObjectPropertyExpression, Stream<OWLObjectPropertyExpression>> roleCover;
        private Function<Integer, Stream<Integer>> intCover;

        public Cover(Function<OWLClassExpression, Stream<OWLClassExpression>> conceptCover,
                Function<OWLObjectPropertyExpression, Stream<OWLObjectPropertyExpression>> roleCover,
                Function<Integer, Stream<Integer>> intCover) {
            this.conceptCover = conceptCover;
            this.roleCover = roleCover;
            this.intCover = intCover;
        }

        /**
         * @return A cached version of this cover.
         */
        public Cover cached() {
            return new Cover(
                    LruCache.wrapStreamFunction(conceptCover),
                    LruCache.wrapStreamFunction(roleCover),
                    LruCache.wrapStreamFunction(intCover));
        }

        public Stream<OWLClassExpression> apply(OWLClassExpression concept) {
            return conceptCover.apply(concept);
        }

        public Stream<OWLObjectPropertyExpression> apply(OWLObjectPropertyExpression role) {
            return roleCover.apply(role);
        }

        public Stream<Integer> apply(int number) {
            return intCover.apply(number);
        }
    }

    public OWLDataFactory df;
    public Ontology refOntology;
    public Set<OWLClassExpression> subConcepts;
    public Set<OWLObjectProperty> simpleRoles;
    public OWLReasoner reasoner;

    /**
     * Creates a new {@code Cover} object for the given reference object.
     *
     * @param refOntology
     *            The ontology used for entailment check.
     * @param simpleRoles
     *            Return only roles that are in this set.
     */
    public Covers(Ontology refOntology, Set<OWLObjectProperty> simpleRoles) {
        df = Ontology.getDefaultDataFactory();
        this.refOntology = refOntology;
        this.reasoner = refOntology.getOwlReasoner();
        this.subConcepts = refOntology.subConcepts().collect(Collectors.toSet());
        this.subConcepts.add(df.getOWLThing());
        this.subConcepts.add(df.getOWLNothing());
        this.simpleRoles = simpleRoles;
    }

    /**
     * @param subclass
     * @param superclass
     * @return True iff the reference ontology of this cover entails that
     *         {@code subclass} is a subclass of {@code superclass}.
     */
    private boolean isSubclass(OWLClassExpression subclass, OWLClassExpression superclass) {
        if (Thread.interrupted()) {
            throw new CanceledException();
        }
        var testAxiom = df.getOWLSubClassOfAxiom(subclass, superclass);
        return reasoner.isEntailed(testAxiom);
    }

    /**
     * For this function, a class A is a strict subclass of B iff A isSubclassOf B
     * is entailed but B isSubclassOf A is not.
     *
     * @param subclass
     * @param superclass
     * @return True iff the reference ontology of this cover entails that
     *         {@code subclass} is a strict subclass of {@code superclass}.
     */
    private boolean isStrictSubclass(OWLClassExpression subclass, OWLClassExpression superclass) {
        return isSubclass(subclass, superclass) && !isSubclass(superclass, subclass);
    }

    /**
     * @param concept
     * @param candidate
     * @return True iff {@code candidate} is in the upward cover of {@code concept}.
     */
    private boolean isInUpCover(OWLClassExpression concept, OWLClassExpression candidate) {
        if (!subConcepts.contains(candidate) || !isSubclass(concept, candidate)) {
            return false;
        } else {
            return !subConcepts.stream()
                    .anyMatch(other -> isStrictSubclass(concept, other) && isStrictSubclass(other, candidate));
        }
    }

    /**
     * @param concept
     * @return All concepts that are in the upward cover of {@code concept}.
     */
    public Stream<OWLClassExpression> upCover(OWLClassExpression concept) {
        return subConcepts.stream()
                .filter(candidate -> isInUpCover(concept, candidate));
    }

    /**
     * @param concept
     * @param candidate
     * @return True iff {@code candidate} is in the downward cover of
     *         {@code concept}.
     */
    private boolean isInDownCover(OWLClassExpression concept, OWLClassExpression candidate) {
        if (!subConcepts.contains(candidate) || !isSubclass(candidate, concept)) {
            return false;
        } else {
            return !subConcepts.stream()
                    .anyMatch(other -> isStrictSubclass(candidate, other) && isStrictSubclass(other, concept));
        }
    }

    /**
     * @param concept
     * @return All concepts that are in the downward cover of {@code concept}.
     */
    public Stream<OWLClassExpression> downCover(OWLClassExpression concept) {
        return subConcepts.stream()
                .filter(candidate -> isInDownCover(concept, candidate));
    }

    /**
     * @return A stream containing all simple roles in the reference ontology.
     */
    private Stream<OWLObjectPropertyExpression> allSimpleRoles() {
        return simpleRoles.stream().flatMap(role -> Stream.of(role, role.getInverseProperty()));
    }

    /**
     * @param subRole
     * @param superRole
     * @return True iff the reference ontology of this cover entails that
     *         {@code subRole} is subsumed by {@code superRole}.
     */
    private boolean isSubRole(OWLObjectPropertyExpression subRole, OWLObjectPropertyExpression superRole) {
        if (Thread.interrupted()) {
            throw new CanceledException();
        }
        var testAxiom = df.getOWLSubObjectPropertyOfAxiom(subRole, superRole);
        return reasoner.isEntailed(testAxiom);
    }

    /**
     * For this function, a class A is a strict subclass of B iff A
     * isSubObjectPropertyOf B is entailed but B isSubObjectPropertyOf A is not.
     *
     * @param subRole
     * @param superRole
     * @return True iff the reference ontology of this cover entails that
     *         {@code subRole} is strictly subsumed by {@code superRole}.
     */
    private boolean isStrictSubRole(OWLObjectPropertyExpression subRole,
            OWLObjectPropertyExpression superRole) {
        return isSubRole(subRole, superRole) && !isSubRole(superRole, subRole);
    }

    /**
     * @param role
     * @param candidate
     * @return True iff {@code candidate} is in the upward cover of {@code role}.
     */
    private boolean isInUpCover(OWLObjectPropertyExpression role, OWLObjectPropertyExpression candidate) {
        if (!simpleRoles.contains(candidate.getNamedProperty()) || !isSubRole(role, candidate)) {
            return false;
        } else {
            return !allSimpleRoles()
                    .anyMatch(other -> isStrictSubRole(role, other) && isStrictSubRole(other, candidate));
        }
    }

    /**
     * @param role
     * @return All role that are in the upward cover of {@code role}.
     */
    public Stream<OWLObjectPropertyExpression> upCover(OWLObjectPropertyExpression role) {
        return allSimpleRoles().filter(candidate -> isInUpCover(role, candidate));
    }

    /**
     * @param role
     * @param candidate
     * @return True iff {@code candidate} is in the downward cover of
     *         {@code role}.
     */
    private boolean isInDownCover(OWLObjectPropertyExpression role, OWLObjectPropertyExpression candidate) {
        if (!simpleRoles.contains(candidate.getNamedProperty()) || !isSubRole(candidate, role)) {
            return false;
        } else {
            return !allSimpleRoles()
                    .anyMatch(other -> isStrictSubRole(candidate, other) && isStrictSubRole(other, role));
        }
    }

    /**
     * @param role
     * @return All roles that are in the downward cover of {@code role}.
     */
    public Stream<OWLObjectPropertyExpression> downCover(OWLObjectPropertyExpression role) {
        return allSimpleRoles().filter(candidate -> isInDownCover(role, candidate));
    }

    /**
     * @param number
     * @return All numbers that are in the downward cover of {@code number}.
     */
    public Stream<Integer> upCover(Integer number) {
        return Stream.of(number, number + 1);
    }

    /**
     * @param number
     * @return All numbers that are in the downward cover of {@code number}.
     */
    public Stream<Integer> downCover(Integer number) {
        if (number == 0) {
            return Stream.of(0);
        } else {
            return Stream.of(number, number - 1);
        }
    }

    /**
     * @return The upward cover, containing concept, role, and number covers.
     */
    public Cover upCover() {
        return new Cover(this::upCover, this::upCover, this::upCover);
    }

    /**
     * @return The downward cover, containing concept, role, and number covers.
     */
    public Cover downCover() {
        return new Cover(this::downCover, this::downCover, this::downCover);
    }

    @Override
    public void close() {
        if (reasoner != null) {
            refOntology.disposeOwlReasoner(reasoner);
            reasoner = null;
        }
    }
}
