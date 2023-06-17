package www.ontologyutils.protege.view.repairs;

import java.util.function.Predicate;

import java.awt.*;
import javax.swing.*;

import www.ontologyutils.protege.view.WrapLayout;
import www.ontologyutils.repair.OntologyRepair;
import www.ontologyutils.toolbox.Ontology;

public abstract class AbstractRepairPane extends JPanel {
    public AbstractRepairPane() {
        setLayout(new WrapLayout(FlowLayout.LEFT));
        addSettings();
    }

    public abstract String getName();

    protected abstract void addSettings();

    public abstract OntologyRepair getRepair(Predicate<Ontology> isRepaired);

    @Override
    public String toString() {
        return getName();
    }
}
