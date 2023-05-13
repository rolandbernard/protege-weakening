package www.ontologyutils.toolbox;

import org.semanticweb.owlapi.model.*;

/**
 * The preorder cache but extended to optimizing specially some constructs of
 * the logic.
 */
public class SubRoleCache extends PreorderCache<OWLObjectPropertyExpression> {
    // Not worth optimizing, because it is fast enough relative to the concept
    // isSubClass computation.
}
