package www.ontologyutils.protege.menu;

import java.awt.event.ActionEvent;

import javax.swing.SwingUtilities;

import org.protege.editor.owl.model.event.*;
import org.protege.editor.owl.ui.action.ProtegeOWLAction;

import www.ontologyutils.toolbox.Ontology;

public abstract class MutationAction extends ProtegeOWLAction implements OWLModelManagerListener {
    @Override
    public void initialise() {
        getOWLModelManager().addListener(this);
        handleChange(null);
    }

    @Override
    public void dispose() {
        getOWLModelManager().removeListener(this);
    }

    @Override
    public void handleChange(OWLModelManagerChangeEvent event) {
        setEnabled(getOWLModelManager().isActiveOntologyMutable());
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
