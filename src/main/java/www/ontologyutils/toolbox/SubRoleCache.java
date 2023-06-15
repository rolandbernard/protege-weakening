package www.ontologyutils.toolbox;

import java.util.Collection;

import org.semanticweb.owlapi.model.*;

/**
 * The preorder cache but extended to optimizing specially some constructs of
 * the logic.
 */
public class SubRoleCache extends PreorderCache<OWLObjectPropertyExpression> {
    /**
     * @param domain
     *            The domain of this preorder. May be incomplete.
     */
    public SubRoleCache(Collection<OWLObjectPropertyExpression> domain) {
        super();
        setupDomain(domain);
    }

    // Not worth optimizing, because it is fast enough relative to the concept
    // isSubClass computation.
}
