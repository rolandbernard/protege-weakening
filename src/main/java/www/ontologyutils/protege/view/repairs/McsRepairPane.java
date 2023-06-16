package www.ontologyutils.protege.view.repairs;

import java.awt.*;
import javax.swing.*;

import java.util.Map;
import java.util.function.Predicate;

import www.ontologyutils.repair.*;
import www.ontologyutils.repair.OntologyRepairRandomMcs.McsComputationStrategy;
import www.ontologyutils.toolbox.Ontology;

public class McsRepairPane extends AbstractRepairPane {
    private McsComputationStrategy mcsStrategy;

    public McsRepairPane() {
        setLayout(new FlowLayout(FlowLayout.LEFT));
        var mcsPanel = new JPanel();
        mcsPanel.add(new Label("Compute"));
        var mcs = new JComboBox<String>();
        mcs.addItem("One MCS");
        mcs.addItem("Some MCS");
        mcs.addItem("All MCS");
        mcs.setSelectedIndex(0);
        var goals = Map.<String, McsComputationStrategy>of("One MCS", McsComputationStrategy.ONE_MCS, "Some MCS",
                McsComputationStrategy.SOME_MCS, "All MCS", McsComputationStrategy.ALL_MCS);
        mcsStrategy = goals.get(mcs.getItemAt(mcs.getSelectedIndex()));
        mcs.addActionListener(e -> {
            mcsStrategy = goals.get(mcs.getItemAt(mcs.getSelectedIndex()));
        });
        mcsPanel.add(mcs);
        add(mcsPanel);
    }

    @Override
    public String getName() {
        return "Maximal Consistent Subset";
    }

    @Override
    public OntologyRepair getRepair(Predicate<Ontology> isRepaired) {
        return new OntologyRepairRandomMcs(isRepaired, mcsStrategy);
    }
}
