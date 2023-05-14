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
    private static final OWLOntologyManager defaultManager = OWLManager.createOWLOntologyManager();

    /**
     * This is only here for statistics
     */
    public static int reasonerCalls;

    private static class CachedReasoner {
        private OWLReasonerFactory reasonerFactory;
        private Set<Ontology> references;
        private OWLReasoner reasoner;

        /**
         * Create a new reasoner cache using the given reasoner factory.
         *
         * @param reasonerFactory
         *            The factory to create the reasoner with if necessary.
         */
        public CachedReasoner(OWLReasonerFactory reasonerFactory) {
            this.reasonerFactory = reasonerFactory;
            this.references = new HashSet<>();
        }

        /**
         * @param ontology
         *            The ontology using this reasoner.
         */
        public void addReference(Ontology ontology) {
            references.add(ontology);
        }

        /**
         * @param ontology
         *            The ontology, no longer using the reasoner after this call.
         */
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
         * @param ontology
         *            The ontology for which to create the reasoner.
         * @return A new {@code OWLReasoner} created using the factory in this cache.
         */
        public OWLReasoner getOwlReasoner(Ontology ontology) {
            try {
                var owlOntology = defaultManager.createOntology();
                defaultManager.addAxioms(owlOntology, Utils.toSet(ontology.axioms()));
                return reasonerFactory.createReasoner(owlOntology);
            } catch (OWLOntologyCreationException e) {
                throw Utils.panic(e);
            }
        }

        /**
         * Use the cached reasoner in this object for executing the given action.
         *
         * @param <T>
         *            The return value of the action to be performed.
         * @param ontology
         *            The ontology on which to create the reasoner.
         * @param action
         *            The action to perform with the reasoner.
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
            reasonerCalls += 1;
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
     *            The set of static (i.e., not to be changed) axioms.
     * @param refutableAxioms
     *            The set of refutable (i.e., to be repaired) axioms.
     * @param reasonerCache
     *            The reasoner cache to be used for reasoning queries.
     */
    private Ontology(Collection<? extends OWLAxiom> staticAxioms, Collection<? extends OWLAxiom> refutableAxioms,
            CachedReasoner reasonerCache) {
        this.staticAxioms = new HashSet<>(staticAxioms);
        this.refutableAxioms = new HashSet<>(refutableAxioms);
        this.refutableAxioms.removeAll(staticAxioms);
        this.reasonerCache = reasonerCache;
        this.reasonerCache.addReference(this);
    }

    /**
     * @param staticAxioms
     *            The set of static (i.e., not to be changed) axioms.
     * @param refutableAxioms
     *            The set of refutable (i.e., to be repaired) axioms.
     * @param reasonerFactory
     *            The reasoner factory to be used for reasoning queries.
     * @return The new ontology.
     */
    public static Ontology withAxioms(Collection<? extends OWLAxiom> staticAxioms,
            Collection<? extends OWLAxiom> refutableAxioms, OWLReasonerFactory reasonerFactory) {
        return new Ontology(staticAxioms, refutableAxioms, new CachedReasoner(reasonerFactory));
    }

    /**
     * @param refutableAxioms
     *            The set of refutable (i.e., to be repaired) axioms.
     * @param reasonerFactory
     *            The reasoner factory to be used for reasoning queries.
     * @return The new ontology.
     */
    public static Ontology withAxioms(Collection<? extends OWLAxiom> refutableAxioms,
            OWLReasonerFactory reasonerFactory) {
        return withAxioms(Set.of(), refutableAxioms, reasonerFactory);
    }

    /**
     * @param reasonerFactory
     *            The reasoner factory to be used for reasoning queries.
     * @return The new empty ontology.
     */
    public static Ontology emptyOntology(OWLReasonerFactory reasonerFactory) {
        return withAxioms(Set.of(), reasonerFactory);
    }

    /**
     * @param ontology
     *            The {@code OWLOntology} form which to copy the axioms. Logical
     *            axiom will be refutable, other will be static.
     * @param reas
     *            The reasoner factory to be used for reasoning queries.
     * @return The new ontology.
     */
    public static Ontology withAxiomsFrom(OWLOntology ontology, OWLReasonerFactory reasonerFactory) {
        var logicalAxioms = ontology.getLogicalAxioms();
        var otherAxioms = Utils.toSet(ontology.getAxioms().stream()
                .filter(axiom -> !logicalAxioms.contains(axiom)));
        return withAxioms(otherAxioms, logicalAxioms, reasonerFactory);
    }

    /**
     * @param filePath
     *            ontaining the ontology.
     * @param reasonerFactory
     *            The reasoner factory to be used for reasoning queries.
     * @return The new ontology, loaded form the file.
     */
    public static Ontology loadOntology(String filePath, OWLReasonerFactory reasonerFactory) {
        OWLOntology ontology = null;
        try {
            var ontologyFile = new File(filePath);
            ontology = defaultManager.loadOntologyFromOntologyDocument(ontologyFile);
            return withAxiomsFrom(ontology, reasonerFactory);
        } catch (OWLOntologyCreationException e) {
            throw Utils.panic(e);
        } finally {
            if (ontology != null) {
                defaultManager.removeOntology(ontology);
            }
        }
    }

    /**
     * Save the ontology to the file given by the path {@code filePath}.
     *
     * @param filePath
     *            The file to which the ontology should be saved.
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

    /**
     * Add/remove axioms form {@code ontology} such that it contains the same axioms
     * as this ontology.
     *
     * @param ontology
     *            The ontology to which we want to add changes.
     * @return true if some change was made to the ontology, false otherwise.
     */
    public boolean applyChangesTo(OWLOntology ontology) {
        var oldAxioms = ontology.getAxioms();
        var newAxioms = Utils.toSet(axioms());
        if (oldAxioms.equals(newAxioms)) {
            return false;
        } else {
            var manager = ontology.getOWLOntologyManager();
            manager.addAxioms(ontology, Utils.toSet(newAxioms.stream().filter(axiom -> !oldAxioms.contains(axiom))));
            manager.removeAxioms(ontology, Utils.toSet(oldAxioms.stream().filter(axiom -> !newAxioms.contains(axiom))));
            return true;
        }
    }

    /**
     * @return The default data factory to use for creating owl api objects.
     */
    public static OWLDataFactory getDefaultDataFactory() {
        return defaultManager.getOWLDataFactory();
    }

    /**
     * @return Stream containing all static axioms of the ontology.
     */
    public Stream<OWLAxiom> staticAxioms() {
        return staticAxioms.stream();
    }

    /**
     * @param types
     *            The types of axioms to return.
     * @return Stream containing all static axioms of the ontology of the given
     *         types.
     */
    public Stream<OWLAxiom> staticAxioms(AxiomType<?>... types) {
        return staticAxioms().filter(axiom -> axiom.isOfType(types));
    }

    /**
     * @param types
     *            The types of axioms to return.
     * @return Stream containing all static axioms of the ontology of the given
     *         types.
     */
    public Stream<OWLAxiom> staticAxioms(Collection<AxiomType<?>> types) {
        return staticAxioms().filter(axiom -> axiom.isOfType(types.toArray(new AxiomType<?>[types.size()])));
    }

    /**
     * @return Stream containing all refutable axioms.
     */
    public Stream<OWLAxiom> refutableAxioms() {
        return refutableAxioms.stream();
    }

    /**
     * @param types
     *            The types of axioms to return.
     * @return Stream containing all refutable axioms of the ontology of the given
     *         types.
     */
    public Stream<OWLAxiom> refutableAxioms(AxiomType<?>... types) {
        return refutableAxioms().filter(axiom -> axiom.isOfType(types));
    }

    /**
     * @param types
     *            The types of axioms to return.
     * @return Stream containing all refutable axioms of the ontology of the given
     *         types.
     */
    public Stream<OWLAxiom> refutableAxioms(Collection<AxiomType<?>> types) {
        return refutableAxioms().filter(axiom -> axiom.isOfType(types.toArray(new AxiomType<?>[types.size()])));
    }

    /**
     * @return Stream containing all axioms.
     */
    public Stream<OWLAxiom> axioms() {
        return Stream.concat(staticAxioms(), refutableAxioms());
    }

    /**
     * @param types
     *            The types of axioms to return.
     * @return Stream containing all axioms of the ontology of the given types.
     */
    public Stream<OWLAxiom> axioms(AxiomType<?>... types) {
        return axioms().filter(axiom -> axiom.isOfType(types));
    }

    /**
     * @param types
     *            The types of axioms to return.
     * @return Stream containing all axioms of the ontology of the given types.
     */
    public Stream<OWLAxiom> axioms(Collection<AxiomType<?>> types) {
        return axioms().filter(axiom -> axiom.isOfType(types.toArray(new AxiomType<?>[types.size()])));
    }

    /**
     * @return Stream containing all logical axioms.
     */
    public Stream<OWLLogicalAxiom> logicalAxioms() {
        return axioms().filter(axiom -> axiom.isLogicalAxiom()).map(axiom -> (OWLLogicalAxiom) axiom);
    }

    /**
     * @return Stream containing all non-logical axioms.
     */
    public Stream<OWLAxiom> nonLogicalAxioms() {
        return axioms().filter(axiom -> !axiom.isLogicalAxiom()).map(axiom -> (OWLAxiom) axiom);
    }

    /**
     * @return Stream containing all TBox axioms.
     */
    public Stream<OWLAxiom> tboxAxioms() {
        return axioms().filter(axiom -> axiom.isOfType(AxiomType.TBoxAxiomTypes));
    }

    /**
     * @return Stream containing all ABox axioms.
     */
    public Stream<OWLAxiom> aboxAxioms() {
        return axioms().filter(axiom -> axiom.isOfType(AxiomType.ABoxAxiomTypes));
    }

    /**
     * @return Stream containing all RBox axioms.
     */
    public Stream<OWLAxiom> rboxAxioms() {
        return axioms().filter(axiom -> axiom.isOfType(AxiomType.RBoxAxiomTypes));
    }

    /**
     * @param axioms
     *            The axioms to remove.
     */
    public void removeAxioms(Stream<? extends OWLAxiom> axioms) {
        axioms.forEach(axiom -> {
            staticAxioms.remove(axiom);
            refutableAxioms.remove(axiom);
        });
    }

    /**
     * @param axioms
     *            The axioms to add.
     */
    public void addStaticAxioms(Stream<? extends OWLAxiom> axioms) {
        axioms.forEach(axiom -> {
            refutableAxioms.remove(axiom);
            staticAxioms.add(axiom);
        });
    }

    /**
     * @param axioms
     *            The axioms to add.
     */
    public void addAxioms(Stream<? extends OWLAxiom> axioms) {
        axioms.forEach(axiom -> {
            staticAxioms.remove(axiom);
            refutableAxioms.add(axiom);
        });
    }

    /**
     * @param axioms
     *            The axioms to remove.
     */
    public void removeAxioms(Collection<? extends OWLAxiom> axioms) {
        removeAxioms(axioms.stream());
    }

    /**
     * @param axioms
     *            The axioms to add.
     */
    public void addStaticAxioms(Collection<? extends OWLAxiom> axioms) {
        addStaticAxioms(axioms.stream());
    }

    /**
     * @param axioms
     *            The axioms to add.
     */
    public void addAxioms(Collection<? extends OWLAxiom> axioms) {
        addAxioms(axioms.stream());
    }

    /**
     * @param axioms
     *            The axioms to remove.
     */
    public void removeAxioms(OWLAxiom... axioms) {
        removeAxioms(Stream.of(axioms));
    }

    /**
     * @param axioms
     *            The axioms to add.
     */
    public void addStaticAxioms(OWLAxiom... axioms) {
        addStaticAxioms(Stream.of(axioms));
    }

    /**
     * @param axioms
     *            The axioms to add.
     */
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

    /**
     * @param origin
     *            The origin axiom.
     * @return The owl annotation for this origin axiom.
     */
    private static OWLAnnotation getNewOriginAnnotation(OWLAxiom origin) {
        var df = getDefaultDataFactory();
        return df.getOWLAnnotation(getOriginAnnotationProperty(), df.getOWLLiteral(origin.toString()));
    }

    /**
     * @param axiom
     *            The axiom for which to get axioms.
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
     *            iom to annotate.
     * @param origin
     *            The origin axiom.
     * @return A new annotated axiom equivalent to {@code axiom}.
     */
    public static OWLAxiom getOriginAnnotatedAxiom(OWLAxiom axiom, OWLAxiom origin) {
        if (axiom.equals(origin)) {
            return axiom;
        } else {
            return axiom.getAnnotatedAxiom(Utils.toSet(axiomOriginAnnotations(origin)));
        }
    }

    /**
     * Replace a axiom with a collection of other axioms. All new axioms will be
     * annotated with the original axioms as the origin.
     *
     * @param remove
     *            ove.
     * @param replacement
     *            The axioms to add.
     */
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

    /**
     * Replace a axiom with a collection of other axioms. All new axioms will be
     * annotated with the original axioms as the origin.
     *
     * @param remove
     *            ove.
     * @param replacement
     *            The axioms to add.
     */
    public void replaceAxiom(OWLAxiom remove, Collection<? extends OWLAxiom> replacement) {
        replaceAxiom(remove, replacement.stream());
    }

    /**
     * Replace a axiom with a collection of other axioms. All new axioms will be
     * annotated with the original axioms as the origin.
     *
     * @param remove
     *            ove.
     * @param replacement
     *            The axioms to add.
     */
    public void replaceAxiom(OWLAxiom remove, OWLAxiom... replacement) {
        replaceAxiom(remove, Stream.of(replacement));
    }

    /**
     * @param remove
     *            The set of axioms not to include in the result.
     * @return The axioms of the ontology without those in {@code remove}.
     */
    public Set<OWLAxiom> complement(Set<OWLAxiom> remove) {
        return Utils.toSet(axioms().filter(axiom -> !remove.contains(axiom)));
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

    /**
     * @return true if the ontology is consistent, false otherwise.
     */
    public boolean isConsistent() {
        return withReasonerDo(reasoner -> reasoner.isConsistent());
    }

    /**
     * @return true if the ontology is coherent, false otherwise.
     */
    public boolean isCoherent() {
        return isConsistent() && isSatisfiable(conceptsInSignature().toArray(n -> new OWLClass[n]));
    }

    /**
     * @param axioms
     *            The axioms to check entailment for.
     * @return true if all of the axioms are entailed, false otherwise.
     */
    public boolean isEntailed(OWLAxiom... axioms) {
        return withReasonerDo(reasoner -> reasoner.isEntailed(Set.of(axioms)));
    }

    /**
     * @param axioms
     *            The axioms to check entailment for.
     * @return true if all of the axioms are entailed, false otherwise.
     */
    public boolean isEntailed(Stream<? extends OWLAxiom> axioms) {
        return withReasonerDo(reasoner -> reasoner.isEntailed(Utils.toSet(axioms)));
    }

    /**
     * @param other
     *            The ontology to test.
     * @return true if {@code other} is entailed by this ontology, false otherwise.
     */
    public boolean isEntailed(Ontology other) {
        return isEntailed(other.logicalAxioms());
    }

    /**
     * @param concepts
     *            The concepts to test.
     * @return true if the concept is satisfiable.
     */
    public boolean isSatisfiable(OWLClassExpression... concepts) {
        return withReasonerDo(reasoner -> {
            for (var concept : concepts) {
                if (!reasoner.isSatisfiable(concept)) {
                    return false;
                }
            }
            return true;
        });
    }

    /**
     * @return A stream with all unsatisfiable atomic concept.
     */
    public Stream<OWLClass> unsatisfiableConcepts() {
        return withReasonerDo(reasoner -> reasoner.getUnsatisfiableClasses().getEntities()).stream();
    }

    /**
     * @param subClass
     *            ub concept.
     * @param superClass
     *            The possible super concept.
     * @return true if the extension of {@code subClass} is a subset of the
     *         extension of {@code superClass} in every model of the ontology, false
     *         otherwise.
     */
    public boolean isSubClass(OWLClassExpression subClass, OWLClassExpression superClass) {
        var df = getDefaultDataFactory();
        return withReasonerDo(reasoner -> reasoner.isEntailed(df.getOWLSubClassOfAxiom(subClass, superClass)));
    }

    /**
     * @return The list of profile reports for all OWL 2 profiles.
     */
    public List<OWLProfileReport> checkOwlProfiles() {
        return withOwlOntologyDo(ontology -> Utils.toList(
                Arrays.stream(Profiles.values()).map(profile -> profile.checkOntology(ontology))));
    }

    /**
     * @return The steam containing all maximal consistent subsets (including static
     *         axioms) of the ontologies axioms.
     */
    public Stream<Set<OWLAxiom>> maximalConsistentSubsets() {
        return minimalCorrectionSubsets().map(this::complement);
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
     * @return The stream containing all maximal subsets (including static axioms)
     *         of the ontologies axioms that satisfy {@code isRepaired}.
     */
    public Stream<Set<OWLAxiom>> maximalConsistentSubsets(Predicate<Ontology> isRepaired) {
        return minimalCorrectionSubsets(isRepaired).map(this::complement);
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
     * @return The stream of largest maximal subsets (including static axioms) of
     *         the ontologies axioms that satisfy {@code isRepaired}.
     */
    public Stream<Set<OWLAxiom>> largestMaximalConsistentSubsets(Predicate<Ontology> isRepaired) {
        return (new MaximalConsistentSubsets(this, isRepaired, true)).stream();
    }

    /**
     * @return A stream of all minimal subsets that when removed from the ontology
     *         yield an optimal classical repair for consistency of the ontology.
     */
    public Stream<Set<OWLAxiom>> minimalCorrectionSubsets() {
        return minimalCorrectionSubsets(Ontology::isConsistent);
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
     * @return A stream of all minimal subsets that when removed from the ontology
     *         yield an optimal classical repair for consistency of the ontology.
     */
    public Stream<Set<OWLAxiom>> minimalCorrectionSubsets(Predicate<Ontology> isRepaired) {
        return MinimalSubsets.allMinimalSubsets(refutableAxioms, axioms -> {
            try (var ontology = new Ontology(staticAxioms, complement(axioms), reasonerCache)) {
                return isRepaired.test(ontology);
            }
        });
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
     * @return A stream of all minimal subsets that are not repaired.
     */
    public Stream<Set<OWLAxiom>> minimalUnsatisfiableSubsets(Predicate<Ontology> isRepaired) {
        return MinimalSubsets.allMinimalSubsets(refutableAxioms, axioms -> {
            try (var ontology = new Ontology(staticAxioms, axioms, reasonerCache)) {
                return !isRepaired.test(ontology);
            }
        });
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
     * @return A stream of smallest minimal subsets that when removed from the
     *         ontology yield an optimal classical repair for consistency of the
     *         ontology.
     */
    public Stream<Set<OWLAxiom>> smallestMinimalCorrectionSubsets(Predicate<Ontology> isRepaired) {
        return (new MaximalConsistentSubsets(this, isRepaired, true)).correctionStream();
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
     * @return A single maximal consistent subset.
     */
    public Set<OWLAxiom> maximalConsistentSubset(Predicate<Ontology> isRepaired) {
        return complement(minimalCorrectionSubset(isRepaired));
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
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
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
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
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
     * @return A stream of some maximal consistent subset.
     */
    public Stream<Set<OWLAxiom>> someMaximalConsistentSubsets(Predicate<Ontology> isRepaired) {
        return someMinimalCorrectionSubsets(isRepaired).map(this::complement);
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
     * @return A stream of some minimal correction subset.
     */
    public Stream<Set<OWLAxiom>> someMinimalCorrectionSubsets(Predicate<Ontology> isRepaired) {
        return MinimalSubsets.randomizedMinimalSubsets(refutableAxioms, 1, axioms -> {
            try (var ontology = new Ontology(staticAxioms, complement(axioms), reasonerCache)) {
                return isRepaired.test(ontology);
            }
        });
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
     * @return A stream of some set with the refutable axioms of a minimal
     *         unsatisfiable subset.
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
        var df = getDefaultDataFactory();
        return Stream.concat(axioms().flatMap(ax -> ax.getNestedClassExpressions().stream()),
                Stream.of(df.getOWLThing(), df.getOWLNothing())).distinct();
    }

    /**
     * @return A stream containing all non-simple roles.
     */
    public Stream<OWLObjectPropertyExpression> nonSimpleRoles() {
        return withOwlOntologyDo(ontology -> (new OWLObjectPropertyManager(ontology.getOWLOntologyManager(), ontology))
                .getNonSimpleProperties()).stream();
    }

    /**
     * @return A stream containing all simple roles.
     */
    public Stream<OWLObjectPropertyExpression> simpleRoles() {
        var nonSimple = Utils.toSet(nonSimpleRoles());
        return rolesInSignature().flatMap(r -> Stream.of(r, r.getInverseProperty()))
                .filter(role -> !nonSimple.contains(role));
    }

    /**
     * @return A stream providing all subconcepts used in the ontology's TBox.
     */
    public Stream<OWLClassExpression> subConceptsOfTbox() {
        var df = getDefaultDataFactory();
        return Stream.concat(tboxAxioms().flatMap(ax -> ax.getNestedClassExpressions().stream()),
                Stream.of(df.getOWLThing(), df.getOWLNothing())).distinct();
    }

    /**
     * @return A str eam containing all entities in the signature of this ontology.
     */
    public Stream<OWLEntity> signature() {
        return axioms().flatMap(ax -> ax.getSignature().stream());
    }

    /**
     * @return A stream containing all concept names in the signature of this
     *         ontology.
     */
    public Stream<OWLClass> conceptsInSignature() {
        return axioms().flatMap(ax -> ax.getClassesInSignature().stream());
    }

    /**
     * @return A stream containing all roles in the signature of this ontology.
     */
    public Stream<OWLObjectProperty> rolesInSignature() {
        return axioms().flatMap(ax -> ax.getObjectPropertiesInSignature().stream());
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
            return Utils.toSet(ontology.getClassesInSignature().stream()
                    .flatMap(left -> ontology.getClassesInSignature().stream()
                            .map(right -> df.getOWLSubClassOfAxiom(left, right))
                            .filter(axiom -> !isConsistent || reasoner.isEntailed(axiom))));
        }).stream();
    }

    /**
     * Some reasoners do not work if some declarations are missing. This will
     * generate all missing declarations and add them as static axioms.
     */
    public void generateDeclarationAxioms() {
        var df = getDefaultDataFactory();
        for (var entity : Utils.toList(signature())) {
            var newAxiom = df.getOWLDeclarationAxiom(entity);
            if (!staticAxioms.contains(newAxiom) && !refutableAxioms.contains(newAxiom)) {
                staticAxioms.add(newAxiom);
            }
        }
    }

    /**
     * Clone this ontology, but only axioms in {@code axioms}.
     *
     * @param axio
     *            The axioms that should be retained.
     * @return The new ontology.
     */
    public Ontology cloneWith(Set<? extends OWLAxiom> axioms) {
        return new Ontology(Utils.toList(staticAxioms.stream().filter(axiom -> axioms.contains(axiom))),
                Utils.toList(refutableAxioms.stream().filter(axiom -> axioms.contains(axiom))), reasonerCache);
    }

    /**
     * Clone this ontology, but only static axioms and those axioms in
     * {@code axioms}.
     *
     * @param axioms
     *            The axioms that should be retained.
     * @return The new ontology.
     */
    public Ontology cloneWithRefutable(Set<? extends OWLAxiom> axioms) {
        return new Ontology(staticAxioms,
                Utils.toList(refutableAxioms.stream().filter(axiom -> axioms.contains(axiom))), reasonerCache);
    }

    /**
     * Clone this ontology, but only static axioms.
     *
     * @return The new ontology.
     */
    public Ontology cloneOnlyStatic() {
        return new Ontology(staticAxioms, Set.of(), reasonerCache);
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
