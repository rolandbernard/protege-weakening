package www.ontologyutils.protege.menu;

import java.awt.event.ActionEvent;

import javax.swing.SwingUtilities;

import org.protege.editor.owl.model.event.*;
import org.protege.editor.owl.ui.action.ProtegeOWLAction;

import www.ontologyutils.toolbox.Ontology;

public abstract class MutationAction extends ProtegeOWLAction {
    private OWLModelManagerListener listener = event -> {
        if (event.isType(EventType.ACTIVE_ONTOLOGY_CHANGED)) {
            updateState();
        }
    };

    private void updateState() {
        setEnabled(getOWLModelManager().isActiveOntologyMutable());
    }

    @Override
    public void initialise() {
        getOWLModelManager().addListener(listener);
        updateState();
    }

    @Override
    public void dispose() {
        getOWLModelManager().removeListener(listener);
    }

    protected abstract void performMutation(final Ontology ontology);

    @Override
    public void actionPerformed(final ActionEvent event) {
        final var thread = new Thread(() -> {
            final var reasonerFactory = getOWLModelManager().getOWLReasonerManager()
                    .getCurrentReasonerFactory().getReasonerFactory();
            final var owlOntology = getOWLModelManager().getActiveOntology();
            final var ontology = Ontology.withAxiomsFrom(owlOntology, reasonerFactory);
            performMutation(ontology);
            SwingUtilities.invokeLater(() -> {
                ontology.applyChangesTo(owlOntology);
            });
        });
        thread.start();
    }
}
