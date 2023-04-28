package www.ontologyutils.protege.menu;

import java.awt.event.ActionEvent;

import www.ontologyutils.normalization.SroiqNormalization;
import www.ontologyutils.toolbox.Ontology;

public class NormalizeToSroiq extends MutationAction {
    private SroiqNormalization normalization = new SroiqNormalization();

    @Override
    public void actionPerformed(final ActionEvent event) {
        final var reasonerFactory = getOWLModelManager().getOWLReasonerManager()
                .getCurrentReasonerFactory().getReasonerFactory();
        final var owlOntology = getOWLModelManager().getActiveOntology();
        final var ontology = Ontology.withAxiomsFrom(owlOntology, reasonerFactory);
        normalization.apply(ontology);
        ontology.applyChangesTo(owlOntology);
    }
}
