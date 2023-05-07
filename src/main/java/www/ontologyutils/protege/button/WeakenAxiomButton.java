package www.ontologyutils.protege.button;

import java.awt.*;

import org.protege.editor.core.ui.list.MListButton;
import org.protege.editor.owl.OWLEditorKit;

import www.ontologyutils.protege.list.AxiomListItem;
import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.repair.OntologyRepairRandomMcs;
import www.ontologyutils.toolbox.*;

public class WeakenAxiomButton extends MListButton {
    public WeakenAxiomButton(AxiomListItem item, OWLEditorKit editorKit) {
        super("Weaken Axiom", Color.GREEN.darker(), e -> {
            var reasonerFactory = editorKit.getOWLModelManager().getOWLReasonerManager()
                    .getCurrentReasonerFactory().getReasonerFactory();
            var owlOntology = item.getOntology();
            try (var ontology = Ontology.withAxiomsFrom(owlOntology, reasonerFactory)) {
                try (var refOntology = OntologyRepairRandomMcs.forConsistency().modified(ontology)) {
                    try (var weakener = new AxiomWeakener(refOntology, refOntology)) {
                        var axiom = item.getAxiom();
                        ontology.replaceAxiom(axiom, Utils.randomChoice(weakener.weakerAxioms(axiom)));
                    }
                }
                ontology.applyChangesTo(owlOntology);
            }
        });
    }
    
    @Override
    public void paintButtonContent(Graphics2D gOuter) {
        Graphics2D g = (Graphics2D) gOuter.create();
        int size = getBounds().height;
        int thickness = (Math.round(size / 8.0f) / 2) * 2;
        int x = getBounds().x;
        int y = getBounds().y;
        g.fillRect(x + size / 2 - thickness / 2, y + size / 4, thickness, size / 2);
        g.rotate(Math.PI / 4, x + size / 2, y + size / 2);
        g.fillRect(x + size / 4 + thickness / 2, y + size / 4 + thickness / 2, thickness, size / 3);
        g.fillRect(x + size / 4 + thickness / 2, y + size / 4 + thickness / 2, size / 3, thickness);
    }
}
