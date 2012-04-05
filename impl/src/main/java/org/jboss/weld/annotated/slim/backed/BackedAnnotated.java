package org.jboss.weld.annotated.slim.backed;

import java.lang.reflect.Type;
import java.util.Set;

import org.jboss.weld.annotated.enhanced.TypeClosureLazyValueHolder;
import org.jboss.weld.annotated.slim.BaseAnnotated;
import org.jboss.weld.util.LazyValueHolder;

public abstract class BackedAnnotated extends BaseAnnotated {

    private final LazyValueHolder<Set<Type>> typeClosure;

    public BackedAnnotated(Type baseType) {
        super(baseType);
        this.typeClosure = new TypeClosureLazyValueHolder(baseType);
    }

    public Set<Type> getTypeClosure() {
        return typeClosure.get();
    }

}