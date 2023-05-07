package www.ontologyutils.protege.list;

import org.protege.editor.core.ui.list.MListItem;
import org.semanticweb.owlapi.model.*;

// We need this, because if we use OWLAxiom directly we can not add any buttons.
public class AxiomListItem implements MListItem {
    private OWLAxiom axiom;
    private OWLOntology ontology;

    public AxiomListItem(OWLAxiom axiom, OWLOntology ontology) {
        this.axiom = axiom;
        this.ontology = ontology;
    }

    public OWLAxiom getAxiom() {
        return axiom;
    }

    public OWLOntology getOntology() {
        return ontology;
    }

    @Override
    public boolean isEditable() {
        return false;
    }

    @Override
    public void handleEdit() {
    }

    @Override
    public boolean isDeleteable() {
        return false;
    }

    @Override
    public boolean handleDelete() {
        return false;
    }

    @Override
    public String getTooltip() {
        return null;
    }
}
