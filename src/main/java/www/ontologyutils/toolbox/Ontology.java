package www.ontologyutils.toolbox;

import java.io.File;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import org.protege.editor.owl.model.inference.ReasonerUtilities;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.profiles.*;
import org.semanticweb.owlapi.reasoner.*;
import org.semanticweb.owlapi.util.OWLObjectPropertyManager;

/**
 * This class represents an ontology and is used for ontologies in this package.
 * The main utility of having this class instead of using {@code OWLOntology} is
 * the ability to reuse reasoner and owl ontology as much as possible. The class
 * also provides some utility functions for loading and saving ontologies.
 *
 * The ontology object must be closed in order to free all resources associated
 * to the {@code OWLOntology} object and the {@code OWLreasoner}.
 */
public class Ontology implements AutoCloseable {
    private static final OWLOntologyManager defaultManager = OWLManager.createOWLOntologyManager();

    private static class CachedReasoner {
        private final Set<Ontology> references;
        private Set<OWLAxiom> oldAxioms;
        private OWLReasoner reasoner;

        /**
         * Create a new reasoner cache using the given reasoner factory.
         *
         * @param reasonerFactory
         */
        public CachedReasoner() {
            this.references = new HashSet<>();
            this.oldAxioms = new HashSet<>();
        }

        public void addReference(final Ontology ontology) {
            references.add(ontology);
        }

        public void removeReference(final Ontology ontology) {
            references.remove(ontology);
            if (references.isEmpty() && reasoner != null) {
                disposeOwlReasoner(reasoner);
                reasoner = null;
            }
        }

        /**
         * If an {@code OWLReasoner} was created using {@code getOwlReasoner} it must be
         * disposed again to free associated resources.
         *
         * @param reasoner
         *            The {@code OWLReasoner} to dispose.
         */
        public void disposeOwlReasoner(final OWLReasoner reasoner) {
            final var owlOntology = reasoner.getRootOntology();
            reasoner.dispose();
            owlOntology.getOWLOntologyManager().removeOntology(owlOntology);
        }

        /**
         * @param axioms
         * @return A new {@code OWLReasoner} created using the factory in this cache.
         */
        public OWLReasoner getOwlReasoner(final Stream<? extends OWLAxiom> axioms) {
            try {
                final var owlOntology = defaultManager.createOntology();
                defaultManager.addAxioms(owlOntology, axioms.collect(Collectors.toSet()));
                return ReasonerUtilities.createReasoner(owlOntology, null, null);
            } catch (final OWLOntologyCreationException e) {
                throw Utils.panic(e);
            }
        }

        /**
         * Use the cached reasoner in this object for executing the given action.
         *
         * @param <T>
         * @param ontology
         * @param action
         * @return The value returned by {@code action}.
         */
        public <T> T withReasonerDo(final Ontology ontology, final Function<OWLReasoner, T> action) {
            final var newAxioms = ontology.axioms().collect(Collectors.toSet());
            return withReasonerDo(newAxioms, action);
        }

        public <T> T withReasonerDo(final Set<OWLAxiom> newAxioms, final Function<OWLReasoner, T> action) {
            if (reasoner == null) {
                reasoner = getOwlReasoner(newAxioms.stream());
            } else if (!newAxioms.equals(oldAxioms)) {
                final var owlOntology = reasoner.getRootOntology();
                defaultManager.addAxioms(owlOntology,
                        newAxioms.stream().filter(axiom -> !oldAxioms.contains(axiom)).collect(Collectors.toSet()));
                defaultManager.removeAxioms(owlOntology,
                        oldAxioms.stream().filter(axiom -> !newAxioms.contains(axiom)).collect(Collectors.toSet()));
                reasoner.flush();
            }
            oldAxioms = newAxioms;
            return action.apply(reasoner);
        }
    }

    private final Set<OWLAxiom> staticAxioms;
    private final Set<OWLAxiom> refutableAxioms;
    private final CachedReasoner reasonerCache;

    /**
     * Create a new ontology around the given static and refutable axioms. Should
     * the need arise to create a reasoner, use {@code reasonerFactory} to create
     * it.
     *
     * @param staticAxioms
     * @param refutableAxioms
     * @param reasonerFactory
     */
    private Ontology(final Collection<? extends OWLAxiom> staticAxioms,
            final Collection<? extends OWLAxiom> refutableAxioms, final CachedReasoner reasonerCache) {
        this.staticAxioms = new HashSet<>(staticAxioms);
        this.refutableAxioms = new HashSet<>(refutableAxioms);
        this.refutableAxioms.removeAll(staticAxioms);
        this.reasonerCache = reasonerCache;
        this.reasonerCache.addReference(this);
    }

    public static Ontology withAxioms(final Collection<? extends OWLAxiom> staticAxioms,
            final Collection<? extends OWLAxiom> refutableAxioms) {
        return new Ontology(staticAxioms, refutableAxioms, new CachedReasoner());
    }

    public static Ontology withAxioms(final Collection<? extends OWLAxiom> refutableAxioms) {
        return withAxioms(Set.of(), refutableAxioms);
    }

    public static Ontology emptyOntology() {
        return withAxioms(Set.of());
    }

    public static Ontology loadOntology(final String filePath) {
        final var ontologyFile = new File(filePath);
        OWLOntology ontology = null;
        try {
            ontology = defaultManager.loadOntologyFromOntologyDocument(ontologyFile);
        } catch (final OWLOntologyCreationException e) {
            throw Utils.panic(e);
        }
        final var logicalAxioms = ontology.getLogicalAxioms();
        final var otherAxioms = ontology.getAxioms().stream()
                .filter(axiom -> !logicalAxioms.contains(axiom))
                .collect(Collectors.toSet());
        defaultManager.removeOntology(ontology);
        return withAxioms(otherAxioms, logicalAxioms);
    }

    public static Ontology loadOnlyLogicalAxioms(final String filePath) {
        final var ontology = loadOntology(filePath);
        ontology.removeAxioms(ontology.nonLogicalAxioms().collect(Collectors.toList()));
        return ontology;
    }

    public static Ontology loadOntologyWithOriginAnnotations(final String filePath) {
        final var ontology = loadOntology(filePath);
        for (final var axiom : ontology.axioms().collect(Collectors.toList())) {
            ontology.replaceAxiom(axiom, axiom);
        }
        return ontology;
    }

    /**
     * Save the ontology to the file given by the path {@code filePath}.
     *
     * @param filePath
     */
    public void saveOntology(String filePath) {
        this.<Void>withOwlOntologyDo(ontology -> {
            final var ontologyFile = new File(filePath);
            try {
                ontology.saveOntology(new FunctionalSyntaxDocumentFormat(), IRI.create(ontologyFile));
            } catch (final OWLOntologyStorageException e) {
                Utils.panic(e);
            }
            return null;
        });
    }

    /**
     * @return The default data factory to use for creating owl api objects.
     */
    public static OWLDataFactory getDefaultDataFactory() {
        return defaultManager.getOWLDataFactory();
    }

    public Stream<OWLAxiom> staticAxioms() {
        return staticAxioms.stream();
    }

    public Stream<OWLAxiom> staticAxioms(final AxiomType<?>... types) {
        return staticAxioms().filter(axiom -> axiom.isOfType(types));
    }

    public Stream<OWLAxiom> staticAxioms(final Collection<AxiomType<?>> types) {
        return staticAxioms().filter(axiom -> axiom.isOfType(Set.copyOf(types)));
    }

    public Stream<OWLAxiom> refutableAxioms() {
        return refutableAxioms.stream();
    }

    public Stream<OWLAxiom> refutableAxioms(final AxiomType<?>... types) {
        return refutableAxioms().filter(axiom -> axiom.isOfType(types));
    }

    public Stream<OWLAxiom> refutableAxioms(final Collection<AxiomType<?>> types) {
        return refutableAxioms().filter(axiom -> axiom.isOfType(Set.copyOf(types)));
    }

    public Stream<OWLAxiom> axioms() {
        return Stream.concat(staticAxioms(), refutableAxioms());
    }

    public Stream<OWLAxiom> axioms(final AxiomType<?>... types) {
        return axioms().filter(axiom -> axiom.isOfType(types));
    }

    public Stream<OWLAxiom> axioms(final Collection<AxiomType<?>> types) {
        return axioms().filter(axiom -> axiom.isOfType(Set.copyOf(types)));
    }

    public Stream<OWLLogicalAxiom> logicalAxioms() {
        return axioms().filter(axiom -> axiom.isLogicalAxiom()).map(axiom -> (OWLLogicalAxiom) axiom);
    }

    public Stream<OWLAxiom> nonLogicalAxioms() {
        return axioms().filter(axiom -> !axiom.isLogicalAxiom()).map(axiom -> (OWLAxiom) axiom);
    }

    public Stream<OWLAxiom> tboxAxioms() {
        return axioms().filter(axiom -> axiom.isOfType(AxiomType.TBoxAxiomTypes));
    }

    public Stream<OWLAxiom> aboxAxioms() {
        return axioms().filter(axiom -> axiom.isOfType(AxiomType.ABoxAxiomTypes));
    }

    public Stream<OWLAxiom> rboxAxioms() {
        return axioms().filter(axiom -> axiom.isOfType(AxiomType.RBoxAxiomTypes));
    }

    public void removeAxioms(final Stream<? extends OWLAxiom> axioms) {
        axioms.forEach(axiom -> {
            staticAxioms.remove(axiom);
            refutableAxioms.remove(axiom);
        });
    }

    public void addStaticAxioms(final Stream<? extends OWLAxiom> axioms) {
        axioms.forEach(axiom -> {
            refutableAxioms.remove(axiom);
            staticAxioms.add(axiom);
        });
    }

    public void addAxioms(final Stream<? extends OWLAxiom> axioms) {
        axioms.forEach(axiom -> {
            staticAxioms.remove(axiom);
            refutableAxioms.add(axiom);
        });
    }

    public void removeAxioms(final Collection<? extends OWLAxiom> axioms) {
        removeAxioms(axioms.stream());
    }

    public void addStaticAxioms(final Collection<? extends OWLAxiom> axioms) {
        addStaticAxioms(axioms.stream());
    }

    public void addAxioms(final Collection<? extends OWLAxiom> axioms) {
        addAxioms(axioms.stream());
    }

    public void removeAxioms(final OWLAxiom... axioms) {
        removeAxioms(Stream.of(axioms));
    }

    public void addStaticAxioms(final OWLAxiom... axioms) {
        addStaticAxioms(Stream.of(axioms));
    }

    public void addAxioms(final OWLAxiom... axioms) {
        addAxioms(Stream.of(axioms));
    }

    /**
     * @return The {@code OWLAnnotationProperty} used for the origin annotation when
     *         replacing axioms.
     */
    public static OWLAnnotationProperty getOriginAnnotationProperty() {
        return getDefaultDataFactory().getOWLAnnotationProperty(IRI.create("origin"));
    }

    private static OWLAnnotation getNewOriginAnnotation(final OWLAxiom origin) {
        final var df = getDefaultDataFactory();
        return df.getOWLAnnotation(getOriginAnnotationProperty(), df.getOWLLiteral(origin.toString()));
    }

    /**
     * @return The annotations of {@code axiom}.
     */
    public static Stream<OWLAnnotation> axiomOriginAnnotations(final OWLAxiom axiom) {
        if (axiom.getAnnotations(getOriginAnnotationProperty()).size() > 0) {
            return axiom.getAnnotations(getOriginAnnotationProperty()).stream();
        } else {
            return Stream.of(getNewOriginAnnotation(axiom));
        }
    }

    /**
     * Annotate {@code axiom} with the origin annotation indicating that the origin
     * of {@code axiom} is {@code origin}.
     *
     * @param axiom
     * @param origin
     * @return A new annotated axiom equivalent to {@code axiom}.
     */
    public static OWLAxiom getOriginAnnotatedAxiom(final OWLAxiom axiom, final OWLAxiom origin) {
        if (axiom.equals(origin)) {
            return axiom;
        } else {
            return axiom.getAnnotatedAxiom(axiomOriginAnnotations(origin).collect(Collectors.toSet()));
        }
    }

    public void replaceAxiom(final OWLAxiom remove, final Stream<? extends OWLAxiom> replacement) {
        removeAxioms(remove);
        addAxioms(replacement.map(a -> getOriginAnnotatedAxiom(a, remove)));
    }

    public void replaceAxiom(final OWLAxiom remove, final Collection<? extends OWLAxiom> replacement) {
        replaceAxiom(remove, replacement.stream());
    }

    public void replaceAxiom(final OWLAxiom remove, final OWLAxiom... replacement) {
        replaceAxiom(remove, Stream.of(replacement));
    }

    /**
     * @param remove
     * @return The axioms of the ontology without those in {@code remove}.
     */
    public Set<OWLAxiom> complement(final Set<OWLAxiom> remove) {
        return axioms().filter(axiom -> !remove.contains(axiom)).collect(Collectors.toSet());
    }

    /**
     * The reasoner created by this call must be disposed of again using the
     * {@code disposeOwlReasoner} method.
     *
     * @return A new reasoner for the ontology.
     */
    public OWLReasoner getOwlReasoner() {
        return reasonerCache.getOwlReasoner(this.axioms());
    }

    /**
     * Dispose of a reasoner to release all resources associated with the
     * {@code OWLOntology} and {@code OWLReasoner}.
     *
     * @param reasoner
     *            The reasoner to dispose of.
     */
    public void disposeOwlReasoner(final OWLReasoner reasoner) {
        reasonerCache.disposeOwlReasoner(reasoner);
    }

    private <T> T withReasonerDo(final Function<OWLReasoner, T> action) {
        return reasonerCache.withReasonerDo(this, action);
    }

    private <T> T withOwlOntologyDo(final Function<OWLOntology, T> action) {
        return reasonerCache.withReasonerDo(this, reasoner -> action.apply(reasoner.getRootOntology()));
    }

    public boolean isConsistent() {
        return withReasonerDo(reasoner -> reasoner.isConsistent());
    }

    public boolean isEntailed(final OWLAxiom... axioms) {
        return withReasonerDo(reasoner -> reasoner.isEntailed(Set.of(axioms)));
    }

    public boolean isEntailed(final Stream<? extends OWLAxiom> axioms) {
        return withReasonerDo(reasoner -> reasoner.isEntailed(axioms.collect(Collectors.toSet())));
    }

    public boolean isEntailed(final Ontology other) {
        return isEntailed(other.logicalAxioms());
    }

    public boolean isSatisfiable(final OWLClassExpression concepts) {
        return withReasonerDo(reasoner -> reasoner.isSatisfiable(concepts));
    }

    public List<OWLProfileReport> checkOwlProfiles() {
        return withOwlOntologyDo(ontology -> Arrays.stream(Profiles.values())
                .map(profile -> profile.checkOntology(ontology))
                .collect(Collectors.toList()));
    }

    public Stream<Set<OWLAxiom>> maximalConsistentSubsets() {
        return (new MaximalConsistentSubsets(this)).stream();
    }

    public Stream<Set<OWLAxiom>> maximalConsistentSubsets(final Predicate<Ontology> isRepaired) {
        return (new MaximalConsistentSubsets(this, isRepaired)).stream();
    }

    public Stream<Set<OWLAxiom>> largestMaximalConsistentSubsets(final Predicate<Ontology> isRepaired) {
        return (new MaximalConsistentSubsets(this, isRepaired, true)).stream();
    }

    /**
     * @return A stream of all sets of axioms that when removed from the ontology
     *         yield an optimal classical repair for consistency of the ontology.
     */
    public Stream<Set<OWLAxiom>> minimalCorrectionSubsets() {
        return (new MaximalConsistentSubsets(this)).correctionStream();
    }

    public Stream<Set<OWLAxiom>> minimalCorrectionSubsets(final Predicate<Ontology> isRepaired) {
        return (new MaximalConsistentSubsets(this)).correctionStream();
    }

    public Stream<Set<OWLAxiom>> smallestMinimalCorrectionSubsets(final Predicate<Ontology> isRepaired) {
        return (new MaximalConsistentSubsets(this)).correctionStream();
    }

    /**
     * @return A single maximal consistent subset.
     */
    public Set<OWLAxiom> maximalConsistentSubset(final Predicate<Ontology> isRepaired) {
        return complement(minimalCorrectionSubset(isRepaired));
    }

    /**
     * @return A single minimal correction subset.
     */
    public Set<OWLAxiom> minimalCorrectionSubset(final Predicate<Ontology> isRepaired) {
        return MinimalSubsets.getRandomizedMinimalSubset(refutableAxioms,
                axioms -> isRepaired.test(new Ontology(staticAxioms, complement(axioms), reasonerCache)));
    }

    /**
     * @return A single set with the refutable axioms of a minimal unsatisfiable
     *         subset.
     */
    public Set<OWLAxiom> minimalUnsatisfiableSubset(final Predicate<Ontology> isRepaired) {
        return MinimalSubsets.getRandomizedMinimalSubset(refutableAxioms,
                axioms -> !isRepaired.test(new Ontology(staticAxioms, axioms, reasonerCache)));
    }

    /**
     * @return A single maximal consistent subset.
     */
    public Stream<Set<OWLAxiom>> someMaximalConsistentSubsets(final Predicate<Ontology> isRepaired) {
        return someMinimalCorrectionSubsets(isRepaired).map(this::complement);
    }

    /**
     * @return A single minimal correction subset.
     */
    public Stream<Set<OWLAxiom>> someMinimalCorrectionSubsets(final Predicate<Ontology> isRepaired) {
        return MinimalSubsets.getRandomizedMinimalSubsets(refutableAxioms, 8,
                axioms -> isRepaired.test(new Ontology(staticAxioms, complement(axioms), reasonerCache))).stream();
    }

    /**
     * @return A single set with the refutable axioms of a minimal unsatisfiable
     *         subset.
     */
    public Stream<Set<OWLAxiom>> someMinimalUnsatisfiableSubsets(final Predicate<Ontology> isRepaired) {
        return MinimalSubsets.getRandomizedMinimalSubsets(refutableAxioms, 8,
                axioms -> !isRepaired.test(new Ontology(staticAxioms, axioms, reasonerCache))).stream();
    }

    /**
     * @return A stream providing all subconcepts used in the ontology.
     */
    public Stream<OWLClassExpression> subConcepts() {
        return axioms().flatMap(axiom -> axiom.getNestedClassExpressions().stream());
    }

    /**
     * @return A stream containing all non-simple roles.
     */
    public Stream<OWLObjectProperty> nonSimpleRoles() {
        return withOwlOntologyDo(
                ontology -> (new OWLObjectPropertyManager(defaultManager, ontology)).getNonSimpleProperties()).stream()
                .map(role -> role.getNamedProperty()).distinct();
    }

    /**
     * @return A stream containing all simple roles.
     */
    public Stream<OWLObjectProperty> simpleRoles() {
        final var nonSimple = withOwlOntologyDo(
                ontology -> (new OWLObjectPropertyManager(defaultManager, ontology)).getNonSimpleProperties()).stream()
                .map(role -> role.getNamedProperty()).collect(Collectors.toSet());
        return rolesInSignature().filter(role -> !nonSimple.contains(role));
    }

    /**
     * @return A stream providing all subconcepts used in the ontology's TBox.
     */
    public Stream<OWLClassExpression> subConceptsOfTbox() {
        return tboxAxioms().flatMap(axiom -> axiom.getNestedClassExpressions().stream());
    }

    /**
     * @return A stream containing all entities in the signature of this ontology.
     */
    public Stream<OWLEntity> signature() {
        return axioms().flatMap(axiom -> axiom.getSignature().stream());
    }

    /**
     * @return A stream containing all concept names in the signature of this
     *         ontology.
     */
    public Stream<OWLClass> conceptsInSignature() {
        return axioms().flatMap(axiom -> axiom.getClassesInSignature().stream());
    }

    /**
     * @return A stream containing all roles in the signature of this ontology.
     */
    public Stream<OWLObjectProperty> rolesInSignature() {
        return axioms().flatMap(axiom -> axiom.getObjectPropertiesInSignature().stream());
    }

    /**
     * @return The stream of C1 subclass C2 axioms, C1 and C2 classes in the
     *         signature
     *         of {@code ontology}, entailed by {@code ontology}.
     */
    public Stream<OWLSubClassOfAxiom> inferredTaxonomyAxioms() {
        return withReasonerDo(reasoner -> {
            final var df = getDefaultDataFactory();
            final var ontology = reasoner.getRootOntology();
            final var isConsistent = reasoner.isConsistent();
            return ontology.getClassesInSignature().stream()
                    .flatMap(left -> ontology.getClassesInSignature().stream()
                            .map(right -> df.getOWLSubClassOfAxiom(left, right))
                            .filter(axiom -> !isConsistent || reasoner.isEntailed(axiom)))
                    .collect(Collectors.toSet());
        }).stream();
    }

    @Override
    public Ontology clone() {
        return new Ontology(staticAxioms, refutableAxioms, reasonerCache);
    }

    @Override
    public void close() {
        reasonerCache.removeReference(this);
    }
}
