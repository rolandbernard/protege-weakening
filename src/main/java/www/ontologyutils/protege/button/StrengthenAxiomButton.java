package www.ontologyutils.protege.button;

import java.awt.Graphics2D;

import org.protege.editor.owl.OWLEditorKit;

import www.ontologyutils.protege.list.AxiomListItem;
import www.ontologyutils.refinement.*;
import www.ontologyutils.toolbox.*;

public class StrengthenAxiomButton extends AbstractAxiomRefinementButton {
    public StrengthenAxiomButton(AxiomListItem item, OWLEditorKit editorKit) {
        super("Strengthen Axiom", item, editorKit);
    }

    @Override
    public AxiomRefinement getRefinement(Ontology refOntology, Ontology fullOntology) {
        return new AxiomStrengthener(refOntology, fullOntology);
    }

    @Override
    public void paintButtonContent(Graphics2D g) {
        drawArrow(g, Math.PI);
    }
}
