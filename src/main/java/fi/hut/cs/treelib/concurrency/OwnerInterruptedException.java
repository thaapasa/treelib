package fi.hut.cs.treelib.concurrency;

import fi.hut.cs.treelib.Owner;

/**
 * Notifies that the owner (transaction or similar) has been interrupted. This
 * can happen, for example, when a deadlock occurs when transactions acquire
 * locks.
 * 
 * @author thaapasa
 */
public class OwnerInterruptedException extends RuntimeException {

    private static final long serialVersionUID = -2172830815543242257L;

    private final Owner owner;
    private final Object target;

    public OwnerInterruptedException(String method, Object target, Owner owner, Throwable cause) {
        super(String.format("%s: %s of %s", method, target, owner), cause);
        this.owner = owner;
        this.target = target;
    }

    public Owner getOwner() {
        return owner;
    }

    public Object getTarget() {
        return target;
    }

}
