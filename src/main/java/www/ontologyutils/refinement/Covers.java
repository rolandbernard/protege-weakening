package www.ontologyutils.refinement;

import java.util.*;
import java.util.function.Function;
import java.util.stream.*;

import org.semanticweb.owlapi.model.*;

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
public class Covers {
    /**
     * Class representing a single cover direction. Contains functions for concepts,
     * roles, and integers.
     */
    public static class Cover {
        private Function<OWLClassExpression, Stream<OWLClassExpression>> conceptCover;
        private Function<OWLObjectPropertyExpression, Stream<OWLObjectPropertyExpression>> roleCover;
        private Function<OWLObjectPropertyExpression, Stream<OWLObjectPropertyExpression>> nonSimpleRoleCover;
        private Function<Integer, Stream<Integer>> intCover;

        /**
         * @param conceptCover
         *            The concept function of this cover.
         * @param roleCover
         *            The (simple) role function of this cover.
         * @param nonSimpleRoleCover
         *            The (non-simple) role function of this cover.
         * @param intCover
         *            The integer function of the over.
         */
        public Cover(Function<OWLClassExpression, Stream<OWLClassExpression>> conceptCover,
                Function<OWLObjectPropertyExpression, Stream<OWLObjectPropertyExpression>> roleCover,
                Function<OWLObjectPropertyExpression, Stream<OWLObjectPropertyExpression>> nonSimpleRoleCover,
                Function<Integer, Stream<Integer>> intCover) {
            this.conceptCover = conceptCover;
            this.roleCover = roleCover;
            this.nonSimpleRoleCover = nonSimpleRoleCover;
            this.intCover = intCover;
        }

        /**
         * @return A cached version of this cover.
         */
        public Cover cached() {
            return new Cover(
                    LruCache.wrapStreamFunction(conceptCover, Integer.MAX_VALUE),
                    LruCache.wrapStreamFunction(roleCover, Integer.MAX_VALUE),
                    LruCache.wrapStreamFunction(nonSimpleRoleCover, Integer.MAX_VALUE),
                    LruCache.wrapStreamFunction(intCover, Integer.MAX_VALUE));
        }

        /**
         * @param concept
         *            The concept for which to compute the cover.
         * @return The stream containing all elements of the cover.
         */
        public Stream<OWLClassExpression> apply(OWLClassExpression concept) {
            return conceptCover.apply(concept);
        }

        /**
         * @param role
         *            The role for which to compute the cover.
         * @param simple
         *            Whether to return only simple roles. See
         *            {@link Covers#upCover(OWLObjectPropertyExpression, boolean)} and
         *            {@link Covers#downCover(OWLObjectPropertyExpression, boolean)}}.
         * @return The stream containing all elements of the cover.
         */
        public Stream<OWLObjectPropertyExpression> apply(OWLObjectPropertyExpression role, boolean simple) {
            return simple ? roleCover.apply(role) : nonSimpleRoleCover.apply(role);
        }

        /**
         * @param number
         *            The integer of which to compute the cover.
         * @return The stream containing all elements of the cover.
         */
        public Stream<Integer> apply(int number) {
            return intCover.apply(number);
        }
    }

    private OWLDataFactory df;
    private Ontology refOntology;
    private Set<OWLClassExpression> subConcepts;
    private Set<OWLObjectPropertyExpression> subRoles;
    private Set<OWLObjectPropertyExpression> simpleRoles;
    private PreorderCache<OWLClassExpression> isSubClass;
    private PreorderCache<OWLObjectPropertyExpression> isSubRole;

    /**
     * Creates a new {@code Cover} object for the given reference object.
     *
     * @param refOntology
     *            The ontology used for entailment check.
     * @param subConcepts
     *            Return only concepts that are in this set.
     * @param subRoles
     *            Return only roles that are in this set.
     * @param simpleRoles
     *            Return only roles that are in this set if asked for simple roles
     *            covers.
     * @param uncached
     *            If true, no subclass relation cache will be created.
     */
    public Covers(Ontology refOntology, Set<OWLClassExpression> subConcepts, Set<OWLObjectPropertyExpression> subRoles,
            Set<OWLObjectPropertyExpression> simpleRoles, boolean uncached) {
        df = Ontology.getDefaultDataFactory();
        this.refOntology = refOntology;
        this.subConcepts = subConcepts;
        this.subRoles = subRoles;
        this.simpleRoles = simpleRoles;
        if (!uncached) {
            this.isSubClass = new SubClassCache();
            this.isSubClass.setupDomain(subConcepts);
            this.isSubRole = new SubRoleCache();
            this.isSubRole.setupDomain(subRoles);
        }
    }

    /**
     * @param subClass
     *            The possible sub concept.
     * @param superClass
     *            The possible super concept.
     * @return True iff the reference ontology of this cover entails that
     *         {@code subclass} is a subclass of {@code superclass}.
     */
    private boolean uncachedIsSubClass(OWLClassExpression subClass, OWLClassExpression superClass) {
        var testAxiom = df.getOWLSubClassOfAxiom(subClass, superClass);
        return refOntology.isEntailed(testAxiom);
    }

    /**
     * @param subClass
     *            The possible sub concept.
     * @param superClass
     *            The possible super concept.
     * @return True iff the reference ontology of this cover entails that
     *         {@code subclass} is a subclass of {@code superclass}.
     */
    private boolean isSubClass(OWLClassExpression subClass, OWLClassExpression superClass) {
        if (isSubClass != null) {
            return isSubClass.computeIfAbsent(subClass, superClass, this::uncachedIsSubClass);
        } else {
            return uncachedIsSubClass(subClass, superClass);
        }
    }

    /**
     * @param subRole
     *            The possible sub role.
     * @param superRole
     *            The possible super role.
     * @return True iff the reference ontology of this cover entails that
     *         {@code subRole} is subsumed by {@code superRole}.
     */
    private boolean uncachedIsSubRole(OWLObjectPropertyExpression subRole, OWLObjectPropertyExpression superRole) {
        var testAxiom = df.getOWLSubObjectPropertyOfAxiom(subRole, superRole);
        return refOntology.isEntailed(testAxiom);
    }

    /**
     * @param subRole
     *            The possible sub role.
     * @param superRole
     *            The possible super role.
     * @return True iff the reference ontology of this cover entails that
     *         {@code subRole} is subsumed by {@code superRole}.
     */
    private boolean isSubRole(OWLObjectPropertyExpression subRole, OWLObjectPropertyExpression superRole) {
        if (isSubRole != null) {
            return isSubRole.computeIfAbsent(subRole, superRole, this::uncachedIsSubRole);
        } else {
            return uncachedIsSubRole(subRole, superRole);
        }
    }

    /**
     * For this function, a class A is a strict subclass of B iff A isSubclassOf B
     * is entailed but B isSubclassOf A is not.
     *
     * @param subClass
     *            The possible sub concept.
     * @param superClass
     *            The possible super concept.
     * @return True iff the reference ontology of this cover entails that
     *         {@code subclass} is a strict subclass of {@code superclass}.
     */
    private boolean isStrictSubClass(OWLClassExpression subClass, OWLClassExpression superClass) {
        return isSubClass(subClass, superClass) && !isSubClass(superClass, subClass);
    }

    /**
     * @param concept
     *            The concept for which to compute the upward cover.
     * @param candidate
     *            The concept for which to check whether it is in the upward cover.
     * @return True iff {@code candidate} is in the upward cover of {@code concept}.
     */
    private boolean isInUpCover(OWLClassExpression concept, OWLClassExpression candidate) {
        if (!subConcepts.contains(candidate) || !isSubClass(concept, candidate)) {
            return false;
        } else if (isSubClass != null) {
            return !Stream.concat(
                    isSubClass.knownStrictPredecessors(candidate).stream()
                            .filter(other -> subConcepts.contains(other)),
                    isSubClass.possibleStrictPredecessors(candidate).stream()
                            .filter(other -> subConcepts.contains(other) && isStrictSubClass(other, candidate)))
                    .anyMatch(other -> isStrictSubClass(concept, other));
        } else {
            return !subConcepts.stream()
                    .anyMatch(other -> isStrictSubClass(other, candidate) && isStrictSubClass(concept, other));
        }
    }

    /**
     * @param concept
     *            The concept for which to compute the upward cover.
     * @return All concepts that are in the upward cover of {@code concept}.
     */
    public Stream<OWLClassExpression> upCover(OWLClassExpression concept) {
        return subConcepts.stream()
                .filter(candidate -> isInUpCover(concept, candidate));
    }

    /**
     * @param concept
     *            The concept for which to compute the downward cover.
     * @param candidate
     *            The concept for which to check whether it is in the downward
     *            cover.
     * @return True iff {@code candidate} is in the downward cover of
     *         {@code concept}.
     */
    private boolean isInDownCover(OWLClassExpression concept, OWLClassExpression candidate) {
        if (!subConcepts.contains(candidate) || !isSubClass(candidate, concept)) {
            return false;
        } else if (isSubClass != null) {
            return !Stream.concat(
                    isSubClass.knownStrictSuccessors(candidate).stream()
                            .filter(other -> subConcepts.contains(other)),
                    isSubClass.possibleStrictSuccessors(candidate).stream()
                            .filter(other -> subConcepts.contains(other) && isStrictSubClass(candidate, other)))
                    .anyMatch(other -> isStrictSubClass(other, concept));
        } else {
            return !subConcepts.stream()
                    .anyMatch(other -> isStrictSubClass(candidate, other) && isStrictSubClass(other, concept));
        }
    }

    /**
     * @param concept
     *            The concept for which to compute the downward cover.
     * @return All concepts that are in the downward cover of {@code concept}.
     */
    public Stream<OWLClassExpression> downCover(OWLClassExpression concept) {
        return subConcepts.stream()
                .filter(candidate -> isInDownCover(concept, candidate));
    }

    /**
     * For this function, a class A is a strict subclass of B iff A
     * isSubObjectPropertyOf B is entailed but B isSubObjectPropertyOf A is not.
     *
     * @param subRole
     *            The possible sub role.
     * @param superRole
     *            The possible super role.
     * @return True iff the reference ontology of this cover entails that
     *         {@code subRole} is strictly subsumed by {@code superRole}.
     */
    private boolean isStrictSubRole(OWLObjectPropertyExpression subRole, OWLObjectPropertyExpression superRole) {
        return isSubRole(subRole, superRole) && !isSubRole(superRole, subRole);
    }

    /**
     * @param role
     *            The role to compute the upward cover for.
     * @param candidate
     *            The role for which to check whether it is in the upward cover.
     * @return True iff {@code candidate} is in the upward cover of {@code role}.
     */
    private boolean isInUpCover(OWLObjectPropertyExpression role, OWLObjectPropertyExpression candidate,
            Set<OWLObjectPropertyExpression> roles) {
        if (!roles.contains(candidate) || !isSubRole(role, candidate)) {
            return false;
        } else if (isSubClass != null) {
            return !Stream.concat(
                    isSubRole.knownStrictPredecessors(candidate).stream()
                            .filter(other -> roles.contains(other)),
                    isSubRole.possibleStrictPredecessors(candidate).stream()
                            .filter(other -> roles.contains(other)
                                    && isStrictSubRole(other, candidate)))
                    .anyMatch(other -> isStrictSubRole(role, other));
        } else {
            return !roles.stream()
                    .anyMatch(other -> isStrictSubRole(other, candidate) && isStrictSubRole(role, other));
        }
    }

    /**
     * @param role
     *            The role for which to compute the upward cover.
     * @param simple
     *            Whether to return only simple roles. This is more than just a
     *            filter applied to the output, but also affects the test of whether
     *            there exist more specific generalizations.
     * @return All role that are in the upward cover of {@code role}.
     */
    public Stream<OWLObjectPropertyExpression> upCover(OWLObjectPropertyExpression role, boolean simple) {
        var allowedRoles = simple ? simpleRoles : subRoles;
        return allowedRoles.stream().filter(candidate -> isInUpCover(role, candidate, allowedRoles));
    }

    /**
     * @param role
     *            The role to find the downward cover for.
     * @param candidate
     *            The candidate to check whether it is in the downward cover.
     * @return True iff {@code candidate} is in the downward cover of
     *         {@code role}.
     */
    private boolean isInDownCover(OWLObjectPropertyExpression role, OWLObjectPropertyExpression candidate,
            Set<OWLObjectPropertyExpression> roles) {
        if (!roles.contains(candidate) || !isSubRole(candidate, role)) {
            return false;
        } else if (isSubClass != null) {
            return !Stream.concat(
                    isSubRole.knownStrictSuccessors(candidate).stream()
                            .filter(other -> roles.contains(other)),
                    isSubRole.possibleStrictSuccessors(candidate).stream()
                            .filter(other -> roles.contains(other)
                                    && isStrictSubRole(candidate, other)))
                    .anyMatch(other -> isStrictSubRole(other, role));
        } else {
            return !roles.stream()
                    .anyMatch(other -> isStrictSubRole(candidate, other) && isStrictSubRole(other, role));
        }
    }

    /**
     * @param role
     *            The role to compute the downward cover for.
     * @param simple
     *            Whether to return only simple roles. This is more than just a
     *            filter applied to the output, but also affects the test of whether
     *            there exist more general specializations.
     * @return All roles that are in the downward cover of {@code role}.
     */
    public Stream<OWLObjectPropertyExpression> downCover(OWLObjectPropertyExpression role, boolean simple) {
        var allowedRoles = simple ? simpleRoles : subRoles;
        return allowedRoles.stream().filter(candidate -> isInDownCover(role, candidate, allowedRoles));
    }

    /**
     * @param number
     *            A non-negative integer to compute the upward cover for.
     * @return All numbers that are in the downward cover of {@code number}.
     */
    public Stream<Integer> upCover(Integer number) {
        return Stream.of(number, number + 1);
    }

    /**
     * @param number
     *            A non-negative integer to compute the downward cover for.
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
        return new Cover(this::upCover, r -> this.upCover(r, true), r -> this.upCover(r, false), this::upCover);
    }

    /**
     * @return The downward cover, containing concept, role, and number covers.
     */
    public Cover downCover() {
        return new Cover(this::downCover, r -> this.downCover(r, true), r -> this.downCover(r, false), this::downCover);
    }
}
