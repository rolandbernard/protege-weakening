package www.ontologyutils.normalization;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.OWLAxiomVisitorExAdapter;

import www.ontologyutils.toolbox.*;

/**
 * Normalization that converts every axiom in the ABox of the ontology to
 * class assertions, (negative) role assertions, or binary (in)equality axioms.
 *
 * This normalization is not strictly necessary, but since the axiom weakener
 * will only remove complete DifferentIndividuals and SameIndividuals axioms,
 * splitting them will make the repair more gentle.
 *
 * SameIndividual axioms can be normalized in different ways. If
 * {@code fullEquality} is:
 * true, it will create all n*(n-1) binary equality axioms between two distinct
 * individual names.
 * false, it will choose one arbitrary individual name as the center and
 * connects all others with (n - 1) binary equality axioms.
 */
public class ABoxNormalization implements OntologyModification {
    /**
     * Visitor class used for converting the axioms.
     */
    private static class Visitor extends OWLAxiomVisitorExAdapter<Collection<OWLAxiom>> {
        protected OWLDataFactory df;
        private boolean fullEquality;

        private Visitor(boolean fullEquality) {
            super(null);
            df = Ontology.getDefaultDataFactory();
            this.fullEquality = fullEquality;
        }

        @Override
        public Collection<OWLAxiom> visit(OWLClassAssertionAxiom axiom) {
            return List.of(axiom);
        }

        @Override
        public Collection<OWLAxiom> visit(OWLObjectPropertyAssertionAxiom axiom) {
            return List.of(axiom);
        }

        @Override
        public Collection<OWLAxiom> visit(OWLNegativeObjectPropertyAssertionAxiom axiom) {
            return List.of(axiom);
        }

        @Override
        public Collection<OWLAxiom> visit(OWLDifferentIndividualsAxiom axiom) {
            var individuals = axiom.getIndividualsAsList();
            return individuals.stream()
                    .flatMap(first -> individuals.stream()
                            .filter(second -> !first.equals(second))
                            .map(second -> (OWLAxiom) df.getOWLDifferentIndividualsAxiom(first, second)))
                    .collect(Collectors.toList());
        }

        @Override
        public Collection<OWLAxiom> visit(OWLSameIndividualAxiom axiom) {
            var individuals = axiom.getIndividualsAsList();
            if (fullEquality) {
                return individuals.stream()
                        .flatMap(first -> individuals.stream()
                                .filter(second -> !first.equals(second))
                                .map(second -> (OWLAxiom) df.getOWLSameIndividualAxiom(first, second)))
                        .collect(Collectors.toList());
            } else {
                var first = individuals.get(0);
                return individuals.stream()
                        .filter(second -> !first.equals(second))
                        .map(second -> (OWLAxiom) df.getOWLSameIndividualAxiom(first, second))
                        .collect(Collectors.toList());
            }
        }

        @Override
        public Collection<OWLAxiom> doDefault(OWLAxiom axiom) {
            throw new IllegalArgumentException("ABox normalization does not support axiom " + axiom);
        }
    }

    private Visitor visitor;

    /**
     * @param fullEquality
     *            Set to true if you want equality asserted between all
     *            pairs of
     *            individuals.
     */
    public ABoxNormalization(boolean fullEquality) {
        visitor = new Visitor(fullEquality);
    }

    public ABoxNormalization() {
        this(false);
    }

    /**
     * @param axiom
     *            The axiom that should be converted.
     * @return A number of sroiq axioms that together are equivalent to
     *         {@code axiom} in every ontology.
     */
    public Stream<OWLAxiom> asSroiqAxioms(OWLAxiom axiom) {
        return axiom.accept(visitor).stream();
    }

    @Override
    public void apply(Ontology ontology) throws IllegalArgumentException {
        var tBox = ontology.aboxAxioms().collect(Collectors.toList());
        ;
        for (var axiom : tBox) {
            ontology.replaceAxiom(axiom, asSroiqAxioms(axiom));
        }
    }
}
