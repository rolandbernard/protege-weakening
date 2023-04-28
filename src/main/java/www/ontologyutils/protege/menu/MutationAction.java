package www.ontologyutils.protege.menu;

import org.protege.editor.owl.model.event.*;
import org.protege.editor.owl.ui.action.ProtegeOWLAction;

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
}
