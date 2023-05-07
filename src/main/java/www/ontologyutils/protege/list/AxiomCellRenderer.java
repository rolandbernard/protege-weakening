package www.ontologyutils.protege.list;

import java.awt.Component;

import javax.swing.JList;
import javax.swing.ListCellRenderer;

import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.ui.renderer.OWLCellRenderer;

public class AxiomCellRenderer implements ListCellRenderer<AxiomListItem> {
    private OWLCellRenderer renderer;

    public AxiomCellRenderer(OWLEditorKit editorKit) {
        renderer = new OWLCellRenderer(editorKit);
        renderer.setHighlightKeywords(true);
        renderer.setWrap(true);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends AxiomListItem> list, AxiomListItem item, int idx,
            boolean selected, boolean focus) {
        return renderer.getListCellRendererComponent(list, item.getAxiom(), idx, selected, focus);
    }
}
