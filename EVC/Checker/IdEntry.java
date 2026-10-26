/*
 * IdEntry.java
 * Represents an entry in the symbol table for an identifier.
 */

package EVC.Checker;

import EVC.ASTs.Decl;

import java.util.Objects;

public final class IdEntry {

    protected final String id;
    protected final Decl attr;
    protected final int level;
    protected final IdEntry previousEntry;

    public IdEntry(String id, Decl attr, int level, IdEntry previousEntry) {
        this.id = Objects.requireNonNull(id, "Identifier must not be null");
        this.attr = Objects.requireNonNull(attr, "Declaration must not be null");
        this.level = level;
        this.previousEntry = previousEntry;
    }

    @Override
    public String toString() {
        return String.format("IdEntry{id='%s', attr=%s, level=%d, previousEntry=%s}",
                id, attr, level, previousEntry != null ? previousEntry.id : "null");
    }
}
