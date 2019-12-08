package decaf.lowlevel.tac;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Class info, for building virtual tables.
 */
public class ClassInfo {
    /**
     * Class name.
     */
    public final String name;

    /**
     * Name of parent class, if any.
     */
    public final Optional<String> parent;

    /**
     * Member variable names.
     */
    public final Set<String> memberVariables;

    /**
     * Method names.
     */
    public final Set<String> Methods;

    /**
     * All method names.
     */
    public final Set<String> methods;

    /**
     * Is it main class?
     */
    public final boolean isMainClass;

    /**
     * Create a class info.
     *
     * @param name            class name
     * @param parent          name of parent class, if any
     * @param memberVariables member variable names
     * @param Methods         methods names
     * @param isMainClass     is it main class?
     */
    public ClassInfo(String name, Optional<String> parent, Set<String> memberVariables,
                     Set<String> Methods, boolean isMainClass) {
        this.name = name;
        this.parent = parent;
        this.memberVariables = memberVariables;
        this.Methods = Methods;
        this.isMainClass = isMainClass;

        var methods = new HashSet<String>();
        methods.addAll(Methods);
        this.methods = methods;
    }
}
