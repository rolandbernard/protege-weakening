package www.ontologyutils.normalization;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.OWLAxiomVisitorExAdapter;

import www.ontologyutils.toolbox.*;

/**
 * Normalization that converts every axiom in the RBox of the ontology to
 * role inclusion axioms, and disjoint role assertions.
 * Some axioms are converted to TBox axioms.
 */
public class RBoxNormalization implements OntologyModification {
    private static final String REFLEXIVE_SUBROLE_NAME = "http://www.ontologyutils.rbox-normalization#[reflexive-subrole]";

    /**
     * Visitor class used for converting the axioms.
     */
    private static class Visitor extends OWLAxiomVisitorExAdapter<Collection<OWLAxiom>> {
        protected final OWLDataFactory df;
        private final boolean fullEquality;

        private Visitor(final boolean fullEquality) {
            super(null);
            df = Ontology.getDefaultDataFactory();
            this.fullEquality = fullEquality;
        }

        @Override
        public Collection<OWLAxiom> visit(final OWLSubObjectPropertyOfAxiom axiom) {
            return List.of(axiom);
        }

        @Override
        public Collection<OWLAxiom> visit(final OWLSubPropertyChainOfAxiom axiom) {
            return List.of(axiom);
        }

        @Override
        public Collection<OWLAxiom> visit(final OWLTransitiveObjectPropertyAxiom axiom) {
            final var property = axiom.getProperty();
            return List.of(df.getOWLSubPropertyChainOfAxiom(List.of(property, property), property));
        }

        @Override
        public Collection<OWLAxiom> visit(final OWLInverseObjectPropertiesAxiom axiom) {
            final var first = axiom.getFirstProperty();
            final var second = axiom.getSecondProperty();
            return List.of(
                    df.getOWLSubObjectPropertyOfAxiom(first.getInverseProperty(), second),
                    df.getOWLSubObjectPropertyOfAxiom(second, first.getInverseProperty()));
        }

        @Override
        public Collection<OWLAxiom> visit(final OWLSymmetricObjectPropertyAxiom axiom) {
            final var property = axiom.getProperty();
            return List.of(df.getOWLSubObjectPropertyOfAxiom(property.getInverseProperty(), property));
        }

        @Override
        public Collection<OWLAxiom> visit(final OWLAsymmetricObjectPropertyAxiom axiom) {
            final var property = axiom.getProperty();
            return List.of(df.getOWLDisjointObjectPropertiesAxiom(property, property.getInverseProperty()));
        }

        @Override
        public Collection<OWLAxiom> visit(final OWLReflexiveObjectPropertyAxiom axiom) {
            final var property = axiom.getProperty();
            final var reflexiveProperty = df.getOWLObjectProperty(IRI.create(REFLEXIVE_SUBROLE_NAME));
            return List.of(
                    df.getOWLSubObjectPropertyOfAxiom(reflexiveProperty, property),
                    df.getOWLSubClassOfAxiom(df.getOWLThing(), df.getOWLObjectHasSelf(reflexiveProperty)));
        }

        @Override
        public Collection<OWLAxiom> visit(final OWLIrreflexiveObjectPropertyAxiom axiom) {
            final var property = axiom.getProperty();
            return List.of(df.getOWLSubClassOfAxiom(df.getOWLThing(),
                    df.getOWLObjectHasSelf(property).getObjectComplementOf()));
        }

        @Override
        public Collection<OWLAxiom> visit(final OWLDisjointObjectPropertiesAxiom axiom) {
            final var properties = List.copyOf(axiom.getProperties());
            return properties.stream()
                    .flatMap(first -> properties.stream()
                            .filter(second -> !first.equals(second))
                            .map(second -> (OWLAxiom) df.getOWLDisjointObjectPropertiesAxiom(first, second)))
                    .collect(Collectors.toList());
        }

        @Override
        public Collection<OWLAxiom> visit(final OWLEquivalentObjectPropertiesAxiom axiom) {
            final var properties = List.copyOf(axiom.getProperties());
            if (fullEquality) {
                return properties.stream()
                        .flatMap(first -> properties.stream()
                                .filter(second -> !first.equals(second))
                                .flatMap(second -> Stream.of(
                                        (OWLAxiom) df.getOWLSubObjectPropertyOfAxiom(second, first),
                                        (OWLAxiom) df.getOWLSubObjectPropertyOfAxiom(first, second))))
                        .collect(Collectors.toList());
            } else {
                final var first = properties.get(0);
                return properties.stream()
                        .filter(second -> !first.equals(second))
                        .flatMap(second -> Stream.of(
                                (OWLAxiom) df.getOWLSubObjectPropertyOfAxiom(second, first),
                                (OWLAxiom) df.getOWLSubObjectPropertyOfAxiom(first, second)))
                        .collect(Collectors.toList());
            }
        }

        @Override
        public Collection<OWLAxiom> doDefault(final OWLAxiom axiom) {
            throw new IllegalArgumentException("RBox normalization does not support axiom " + axiom);
        }
    }

    private final Visitor visitor;

    /**
     * @param fullEquality
     *            Set to true if you want equality asserted between all
     *            pairs of individuals.
     */
    public RBoxNormalization(final boolean fullEquality) {
        visitor = new Visitor(fullEquality);
    }

    public RBoxNormalization() {
        this(false);
    }

    /**
     * Add an axiom defining the reflexive subrole that is used during
     * normalization.
     * Note that this method is intended only for testing, to ensure the original
     * and normalized ontologies are equivalent it will assert that the returned
     * object property contains only reflexive connections.
     *
     * @param ontology
     *            The ontology to which the axioms should be added.
     * @return The reflexive object property.
     */
    public static OWLObjectProperty addSimpleReflexiveRole(final Ontology ontology) {
        final var df = Ontology.getDefaultDataFactory();
        final var reflexiveProperty = df.getOWLObjectProperty(IRI.create(REFLEXIVE_SUBROLE_NAME));
        ontology.addAxioms(
                df.getOWLSubClassOfAxiom(df.getOWLThing(), df.getOWLObjectHasSelf(reflexiveProperty)),
                df.getOWLSubClassOfAxiom(df.getOWLThing(), df.getOWLObjectMaxCardinality(1, reflexiveProperty)));
        return reflexiveProperty;
    }

    /**
     * @param axiom
     *            The axiom that should be converted.
     * @return A number of sroiq axioms that together are equivalent to
     *         {@code axiom} in every ontology.
     */
    public Stream<OWLAxiom> asSroiqAxioms(final OWLAxiom axiom) {
        return axiom.accept(visitor).stream();
    }

    @Override
    public void apply(final Ontology ontology) throws IllegalArgumentException {
        final var tBox = ontology.rboxAxioms().collect(Collectors.toList());
        for (final var axiom : tBox) {
            ontology.replaceAxiom(axiom, asSroiqAxioms(axiom));
        }
    }
}
