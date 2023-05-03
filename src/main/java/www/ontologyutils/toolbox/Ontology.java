package www.ontologyutils.toolbox;

import java.io.File;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

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
    private static OWLOntologyManager defaultManager = OWLManager.createOWLOntologyManager();

    private static class CachedReasoner {
        private OWLReasonerFactory reasonerFactory;
        private Set<Ontology> references;
        private OWLReasoner reasoner;

        /**
         * Create a new reasoner cache using the given reasoner factory.
         *
         * @param reasonerFactory
         */
        public CachedReasoner(OWLReasonerFactory reasonerFactory) {
            this.reasonerFactory = reasonerFactory;
            this.references = new HashSet<>();
        }

        public void addReference(Ontology ontology) {
            references.add(ontology);
        }

        public void removeReference(Ontology ontology) {
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
        public void disposeOwlReasoner(OWLReasoner reasoner) {
            var owlOntology = reasoner.getRootOntology();
            reasoner.dispose();
            owlOntology.getOWLOntologyManager().removeOntology(owlOntology);
        }

        /**
         * @param axioms
         * @return A new {@code OWLReasoner} created using the factory in this cache.
         */
        public OWLReasoner getOwlReasoner(Ontology ontology) {
            try {
                var owlOntology = defaultManager.createOntology();
                defaultManager.addAxioms(owlOntology, ontology.axioms().collect(Collectors.toSet()));
                return reasonerFactory.createReasoner(owlOntology);
            } catch (OWLOntologyCreationException e) {
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
        public <T> T withReasonerDo(Ontology ontology, Function<OWLReasoner, T> action) {
            if (Thread.interrupted()) {
                throw new CanceledException();
            } else if (reasoner == null) {
                reasoner = getOwlReasoner(ontology);
            } else {
                var owlOntology = reasoner.getRootOntology();
                if (ontology.applyChangesTo(owlOntology)) {
                    reasoner.flush();
                }
            }
            return action.apply(reasoner);
        }
    }

    private Set<OWLAxiom> staticAxioms;
    private Set<OWLAxiom> refutableAxioms;
    private CachedReasoner reasonerCache;

    /**
     * Create a new ontology around the given static and refutable axioms. Should
     * the need arise to create a reasoner, use {@code reasonerFactory} to create
     * it.
     *
     * @param staticAxioms
     * @param refutableAxioms
     * @param reasonerFactory
     */
    private Ontology(Collection<? extends OWLAxiom> staticAxioms,
            Collection<? extends OWLAxiom> refutableAxioms, CachedReasoner reasonerCache) {
        this.staticAxioms = new HashSet<>(staticAxioms);
        this.refutableAxioms = new HashSet<>(refutableAxioms);
        this.refutableAxioms.removeAll(staticAxioms);
        this.reasonerCache = reasonerCache;
        this.reasonerCache.addReference(this);
    }

    public static Ontology withAxioms(Collection<? extends OWLAxiom> staticAxioms,
            Collection<? extends OWLAxiom> refutableAxioms,
            OWLReasonerFactory reasonerFactory) {
        return new Ontology(staticAxioms, refutableAxioms, new CachedReasoner(reasonerFactory));
    }

    public static Ontology withAxioms(Collection<? extends OWLAxiom> refutableAxioms,
            OWLReasonerFactory reasonerFactory) {
        return withAxioms(Set.of(), refutableAxioms, reasonerFactory);
    }

    public static Ontology emptyOntology(OWLReasonerFactory reasonerFactory) {
        return withAxioms(Set.of(), reasonerFactory);
    }

    public static Ontology withAxiomsFrom(OWLOntology ontology, OWLReasonerFactory reasonerFactory) {
        var logicalAxioms = ontology.getLogicalAxioms();
        var otherAxioms = ontology.getAxioms().stream()
                .filter(axiom -> !logicalAxioms.contains(axiom))
                .collect(Collectors.toSet());
        return withAxioms(otherAxioms, logicalAxioms, reasonerFactory);
    }

    public static Ontology loadOntology(String filePath, OWLReasonerFactory reasonerFactory) {
        OWLOntology ontology = null;
        try {
            var ontologyFile = new File(filePath);
            ontology = defaultManager.loadOntologyFromOntologyDocument(ontologyFile);
            return withAxiomsFrom(ontology, reasonerFactory);
        } catch (OWLOntologyCreationException e) {
            throw Utils.panic(e);
        } finally {
            defaultManager.removeOntology(ontology);
        }
    }

    public static Ontology loadOnlyLogicalAxioms(String filePath, OWLReasonerFactory reasonerFactory) {
        var ontology = loadOntology(filePath, reasonerFactory);
        ontology.removeAxioms(ontology.nonLogicalAxioms().collect(Collectors.toList()));
        return ontology;
    }

    public static Ontology loadOntologyWithOriginAnnotations(String filePath,
            OWLReasonerFactory reasonerFactory) {
        var ontology = loadOntology(filePath, reasonerFactory);
        for (var axiom : ontology.axioms().collect(Collectors.toList())) {
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
            var ontologyFile = new File(filePath);
            try {
                ontology.saveOntology(new FunctionalSyntaxDocumentFormat(), IRI.create(ontologyFile));
            } catch (OWLOntologyStorageException e) {
                Utils.panic(e);
            }
            return null;
        });
    }

    public boolean applyChangesTo(OWLOntology owlOntology) {
        var oldAxioms = owlOntology.getAxioms();
        var newAxioms = axioms().collect(Collectors.toSet());
        if (oldAxioms.equals(newAxioms)) {
            return false;
        } else {
            var manager = owlOntology.getOWLOntologyManager();
            manager.addAxioms(owlOntology,
                    newAxioms.stream().filter(axiom -> !oldAxioms.contains(axiom)).collect(Collectors.toSet()));
            manager.removeAxioms(owlOntology,
                    oldAxioms.stream().filter(axiom -> !newAxioms.contains(axiom)).collect(Collectors.toSet()));
            return true;
        }
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

    public Stream<OWLAxiom> staticAxioms(AxiomType<?>... types) {
        return staticAxioms().filter(axiom -> axiom.isOfType(types));
    }

    public Stream<OWLAxiom> staticAxioms(Collection<AxiomType<?>> types) {
        return staticAxioms().filter(axiom -> axiom.isOfType(Set.copyOf(types)));
    }

    public Stream<OWLAxiom> refutableAxioms() {
        return refutableAxioms.stream();
    }

    public Stream<OWLAxiom> refutableAxioms(AxiomType<?>... types) {
        return refutableAxioms().filter(axiom -> axiom.isOfType(types));
    }

    public Stream<OWLAxiom> refutableAxioms(Collection<AxiomType<?>> types) {
        return refutableAxioms().filter(axiom -> axiom.isOfType(Set.copyOf(types)));
    }

    public Stream<OWLAxiom> axioms() {
        return Stream.concat(staticAxioms(), refutableAxioms());
    }

    public Stream<OWLAxiom> axioms(AxiomType<?>... types) {
        return axioms().filter(axiom -> axiom.isOfType(types));
    }

    public Stream<OWLAxiom> axioms(Collection<AxiomType<?>> types) {
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

    public void removeAxioms(Stream<? extends OWLAxiom> axioms) {
        axioms.forEach(axiom -> {
            staticAxioms.remove(axiom);
            refutableAxioms.remove(axiom);
        });
    }

    public void addStaticAxioms(Stream<? extends OWLAxiom> axioms) {
        axioms.forEach(axiom -> {
            refutableAxioms.remove(axiom);
            staticAxioms.add(axiom);
        });
    }

    public void addAxioms(Stream<? extends OWLAxiom> axioms) {
        axioms.forEach(axiom -> {
            staticAxioms.remove(axiom);
            refutableAxioms.add(axiom);
        });
    }

    public void removeAxioms(Collection<? extends OWLAxiom> axioms) {
        removeAxioms(axioms.stream());
    }

    public void addStaticAxioms(Collection<? extends OWLAxiom> axioms) {
        addStaticAxioms(axioms.stream());
    }

    public void addAxioms(Collection<? extends OWLAxiom> axioms) {
        addAxioms(axioms.stream());
    }

    public void removeAxioms(OWLAxiom... axioms) {
        removeAxioms(Stream.of(axioms));
    }

    public void addStaticAxioms(OWLAxiom... axioms) {
        addStaticAxioms(Stream.of(axioms));
    }

    public void addAxioms(OWLAxiom... axioms) {
        addAxioms(Stream.of(axioms));
    }

    /**
     * @return The {@code OWLAnnotationProperty} used for the origin annotation when
     *         replacing axioms.
     */
    public static OWLAnnotationProperty getOriginAnnotationProperty() {
        return getDefaultDataFactory().getOWLAnnotationProperty(IRI.create("origin"));
    }

    private static OWLAnnotation getNewOriginAnnotation(OWLAxiom origin) {
        var df = getDefaultDataFactory();
        return df.getOWLAnnotation(getOriginAnnotationProperty(), df.getOWLLiteral(origin.toString()));
    }

    /**
     * @return The annotations of {@code axiom}.
     */
    public static Stream<OWLAnnotation> axiomOriginAnnotations(OWLAxiom axiom) {
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
    public static OWLAxiom getOriginAnnotatedAxiom(OWLAxiom axiom, OWLAxiom origin) {
        if (axiom.equals(origin)) {
            return axiom;
        } else {
            return axiom.getAnnotatedAxiom(axiomOriginAnnotations(origin).collect(Collectors.toSet()));
        }
    }

    public void replaceAxiom(OWLAxiom remove, Stream<? extends OWLAxiom> replacement) {
        var annotated = replacement.map(a -> getOriginAnnotatedAxiom(a, remove));
        boolean isStatic = staticAxioms.contains(remove);
        removeAxioms(remove);
        if (isStatic) {
            addStaticAxioms(annotated);
        } else {
            addAxioms(annotated);
        }
    }

    public void replaceAxiom(OWLAxiom remove, Collection<? extends OWLAxiom> replacement) {
        replaceAxiom(remove, replacement.stream());
    }

    public void replaceAxiom(OWLAxiom remove, OWLAxiom... replacement) {
        replaceAxiom(remove, Stream.of(replacement));
    }

    /**
     * @param remove
     * @return The axioms of the ontology without those in {@code remove}.
     */
    public Set<OWLAxiom> complement(Set<OWLAxiom> remove) {
        return axioms().filter(axiom -> !remove.contains(axiom)).collect(Collectors.toSet());
    }

    /**
     * The reasoner created by this call must be disposed of again using the
     * {@code disposeOwlReasoner} method.
     *
     * @return A new reasoner for the ontology.
     */
    public OWLReasoner getOwlReasoner() {
        return reasonerCache.getOwlReasoner(this);
    }

    /**
     * Dispose of a reasoner to release all resources associated with the
     * {@code OWLOntology} and {@code OWLReasoner}.
     *
     * @param reasoner
     *            The reasoner to dispose of.
     */
    public void disposeOwlReasoner(OWLReasoner reasoner) {
        reasonerCache.disposeOwlReasoner(reasoner);
    }

    private <T> T withReasonerDo(Function<OWLReasoner, T> action) {
        return reasonerCache.withReasonerDo(this, action);
    }

    private <T> T withOwlOntologyDo(Function<OWLOntology, T> action) {
        return reasonerCache.withReasonerDo(this, reasoner -> action.apply(reasoner.getRootOntology()));
    }

    public boolean isConsistent() {
        return withReasonerDo(reasoner -> reasoner.isConsistent());
    }

    public boolean isCoherent() {
        return withReasonerDo(reasoner -> reasoner.isConsistent()
                && reasoner.getUnsatisfiableClasses().getEntitiesMinusBottom().isEmpty());
    }

    public boolean isEntailed(OWLAxiom... axioms) {
        return withReasonerDo(reasoner -> reasoner.isEntailed(Set.of(axioms)));
    }

    public boolean isEntailed(Stream<? extends OWLAxiom> axioms) {
        return withReasonerDo(reasoner -> reasoner.isEntailed(axioms.collect(Collectors.toSet())));
    }

    public boolean isEntailed(Ontology other) {
        return isEntailed(other.logicalAxioms());
    }

    public boolean isSatisfiable(OWLClassExpression concepts) {
        return withReasonerDo(reasoner -> reasoner.isSatisfiable(concepts));
    }

    public List<OWLProfileReport> checkOwlProfiles() {
        return withOwlOntologyDo(ontology -> Arrays.stream(Profiles.values())
                .map(profile -> profile.checkOntology(ontology))
                .collect(Collectors.toList()));
    }

    public Stream<Set<OWLAxiom>> maximalConsistentSubsets() {
        return minimalCorrectionSubsets().map(this::complement);
    }

    public Stream<Set<OWLAxiom>> maximalConsistentSubsets(Predicate<Ontology> isRepaired) {
        return minimalCorrectionSubsets(isRepaired).map(this::complement);
    }

    public Stream<Set<OWLAxiom>> largestMaximalConsistentSubsets(Predicate<Ontology> isRepaired) {
        return (new MaximalConsistentSubsets(this, isRepaired, true)).stream();
    }

    /**
     * @return A stream of all sets of axioms that when removed from the ontology
     *         yield an optimal classical repair for consistency of the ontology.
     */
    public Stream<Set<OWLAxiom>> minimalCorrectionSubsets() {
        return minimalCorrectionSubsets(Ontology::isConsistent);
    }

    public Stream<Set<OWLAxiom>> minimalCorrectionSubsets(Predicate<Ontology> isRepaired) {
        return MinimalSubsets.allMinimalSubsets(refutableAxioms, axioms -> {
            try (var ontology = new Ontology(staticAxioms, complement(axioms), reasonerCache)) {
                return isRepaired.test(ontology);
            }
        });
    }

    public Stream<Set<OWLAxiom>> minimalUnsatisfiableSubsets(Predicate<Ontology> isRepaired) {
        return MinimalSubsets.allMinimalSubsets(refutableAxioms, axioms -> {
            try (var ontology = new Ontology(staticAxioms, axioms, reasonerCache)) {
                return !isRepaired.test(ontology);
            }
        });
    }

    public Stream<Set<OWLAxiom>> smallestMinimalCorrectionSubsets(Predicate<Ontology> isRepaired) {
        return (new MaximalConsistentSubsets(this, isRepaired, true)).correctionStream();
    }

    /**
     * @return A single maximal consistent subset.
     */
    public Set<OWLAxiom> maximalConsistentSubset(Predicate<Ontology> isRepaired) {
        return complement(minimalCorrectionSubset(isRepaired));
    }

    /**
     * @return A single minimal correction subset.
     */
    public Set<OWLAxiom> minimalCorrectionSubset(Predicate<Ontology> isRepaired) {
        return MinimalSubsets.getRandomizedMinimalSubset(refutableAxioms, axioms -> {
            try (var ontology = new Ontology(staticAxioms, complement(axioms), reasonerCache)) {
                return isRepaired.test(ontology);
            }
        });
    }

    /**
     * @return A single set with the refutable axioms of a minimal unsatisfiable
     *         subset.
     */
    public Set<OWLAxiom> minimalUnsatisfiableSubset(Predicate<Ontology> isRepaired) {
        return MinimalSubsets.getRandomizedMinimalSubset(refutableAxioms, axioms -> {
            try (var ontology = new Ontology(staticAxioms, axioms, reasonerCache)) {
                return !isRepaired.test(ontology);
            }
        });
    }

    /**
     * @return A single maximal consistent subset.
     */
    public Stream<Set<OWLAxiom>> someMaximalConsistentSubsets(Predicate<Ontology> isRepaired) {
        return someMinimalCorrectionSubsets(isRepaired).map(this::complement);
    }

    /**
     * @return A single minimal correction subset.
     */
    public Stream<Set<OWLAxiom>> someMinimalCorrectionSubsets(Predicate<Ontology> isRepaired) {
        return MinimalSubsets.randomizedMinimalSubsets(refutableAxioms, 1, axioms -> {
            try (var ontology = new Ontology(staticAxioms, complement(axioms), reasonerCache)) {
                return isRepaired.test(ontology);
            }
        });
    }

    /**
     * @return A single set with the refutable axioms of a minimal unsatisfiable
     *         subset.
     */
    public Stream<Set<OWLAxiom>> someMinimalUnsatisfiableSubsets(Predicate<Ontology> isRepaired) {
        return MinimalSubsets.randomizedMinimalSubsets(refutableAxioms, 1, axioms -> {
            try (var ontology = new Ontology(staticAxioms, axioms, reasonerCache)) {
                return !isRepaired.test(ontology);
            }
        });
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
        var nonSimple = withOwlOntologyDo(
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
            var df = getDefaultDataFactory();
            var ontology = reasoner.getRootOntology();
            var isConsistent = reasoner.isConsistent();
            return ontology.getClassesInSignature().stream()
                    .flatMap(left -> ontology.getClassesInSignature().stream()
                            .map(right -> df.getOWLSubClassOfAxiom(left, right))
                            .filter(axiom -> !isConsistent || reasoner.isEntailed(axiom)))
                    .collect(Collectors.toSet());
        }).stream();
    }

    /**
     * Clone this ontology, but only retain axioms in {@code axioms}.
     *
     * @param axioms
     *            The axioms that should be retained.
     * @return The new ontology.
     */
    public Ontology cloneWith(Set<? extends OWLAxiom> axioms) {
        return new Ontology(staticAxioms.stream().filter(axiom -> axioms.contains(axiom)).collect(Collectors.toList()),
                refutableAxioms.stream().filter(axiom -> axioms.contains(axiom)).collect(Collectors.toList()),
                reasonerCache);
    }

    /**
     * Clone this ontology, but give it a separate reasoner.
     *
     * @return The new ontology.
     */
    public Ontology cloneWithSeparateCache() {
        return new Ontology(staticAxioms, refutableAxioms, new CachedReasoner(reasonerCache.reasonerFactory));
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
