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

    protected abstract boolean performMutation(Ontology ontology);

    @Override
    public void actionPerformed(ActionEvent event) {
        var thread = new Thread(() -> {
            var reasonerFactory = getOWLModelManager().getOWLReasonerManager()
                    .getCurrentReasonerFactory().getReasonerFactory();
            var owlOntology = getOWLModelManager().getActiveOntology();
            var ontology = Ontology.withAxiomsFrom(owlOntology, reasonerFactory);
            if (performMutation(ontology)) {
                SwingUtilities.invokeLater(() -> {
                    ontology.applyChangesTo(owlOntology);
                    ontology.close();
                });
            }
        });
        thread.start();
    }
}
