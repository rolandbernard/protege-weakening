package www.ontologyutils.protege.button;

import org.protege.editor.owl.OWLEditorKit;

import www.ontologyutils.protege.list.AxiomListItem;
import www.ontologyutils.refinement.*;
import www.ontologyutils.toolbox.*;

public class WeakenAxiomButton extends AbstractAxiomRefinementButton {
    public WeakenAxiomButton(AxiomListItem item, OWLEditorKit editorKit) {
        super("Weaken Axiom", item, editorKit);
    }

    @Override
    public AxiomRefinement getRefinement(Ontology refOntology, Ontology fullOntology) {
        return new AxiomWeakener(refOntology, fullOntology);
    }
}
