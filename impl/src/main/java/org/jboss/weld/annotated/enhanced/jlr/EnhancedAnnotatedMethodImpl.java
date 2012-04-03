/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat, Inc., and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.weld.annotated.enhanced.jlr;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedParameter;

import org.jboss.weld.annotated.enhanced.EnhancedAnnotatedMethod;
import org.jboss.weld.annotated.enhanced.EnhancedAnnotatedParameter;
import org.jboss.weld.annotated.enhanced.EnhancedAnnotatedType;
import org.jboss.weld.annotated.enhanced.MethodSignature;
import org.jboss.weld.resources.ClassTransformer;
import org.jboss.weld.util.reflection.Formats;
import org.jboss.weld.util.reflection.Reflections;
import org.jboss.weld.util.reflection.SecureReflections;

/**
 * Represents an annotated method
 * <p/>
 * This class is immutable and therefore threadsafe
 *
 * @param <T>
 * @author Pete Muir
 */
public class EnhancedAnnotatedMethodImpl<T, X> extends AbstractEnhancedAnnotatedCallable<T, X, Method> implements EnhancedAnnotatedMethod<T, X> {

    // The abstracted parameters
    private final ArrayList<EnhancedAnnotatedParameter<?, X>> parameters;

    // The property name
    private final String propertyName;

    private final MethodSignature signature;

    private volatile Map<Class<?>, Method> methods;

    private final AnnotatedMethod<X> slim;

    public static <T, X, Y extends X> EnhancedAnnotatedMethodImpl<T, X> of(AnnotatedMethod<X> annotatedMethod, EnhancedAnnotatedType<Y> declaringClass, ClassTransformer classTransformer) {
        EnhancedAnnotatedType<X> downcastDeclaringType = Reflections.cast(declaringClass);
        return new EnhancedAnnotatedMethodImpl<T, X>(annotatedMethod, buildAnnotationMap(annotatedMethod.getAnnotations()), buildAnnotationMap(annotatedMethod.getAnnotations()), downcastDeclaringType, classTransformer);
    }

    /**
     * Constructor
     * <p/>
     * Initializes the superclass with the built annotation map, sets the method
     * and declaring class abstraction and detects the actual type arguments
     *
     * @param method         The underlying method
     * @param declaringClass The declaring class abstraction
     */
    private EnhancedAnnotatedMethodImpl(AnnotatedMethod<X> annotatedMethod, Map<Class<? extends Annotation>, Annotation> annotationMap, Map<Class<? extends Annotation>, Annotation> declaredAnnotationMap, EnhancedAnnotatedType<X> declaringClass, ClassTransformer classTransformer) {
        super(annotatedMethod, annotationMap, declaredAnnotationMap, classTransformer, declaringClass);
        this.slim = annotatedMethod;
        this.parameters = new ArrayList<EnhancedAnnotatedParameter<?, X>>(annotatedMethod.getParameters().size());
        this.methods = Collections.<Class<?>, Method>singletonMap(annotatedMethod.getJavaMember().getDeclaringClass(), annotatedMethod.getJavaMember());

        validateParameterCount(annotatedMethod);
        for (AnnotatedParameter<X> annotatedParameter : annotatedMethod.getParameters()) {
            EnhancedAnnotatedParameter<?, X> parameter = EnhancedAnnotatedParameterImpl.of(annotatedParameter, this, classTransformer);
            this.parameters.add(parameter);
        }
        this.parameters.trimToSize();

        String propertyName = Reflections.getPropertyName(getDelegate());
        if (propertyName == null) {
            this.propertyName = getName();
        } else {
            this.propertyName = propertyName;
        }
        this.signature = new MethodSignatureImpl(this);

    }

    @Override
    public Method getDelegate() {
        return slim.getJavaMember();
    }

    public List<EnhancedAnnotatedParameter<?, X>> getEnhancedParameters() {
        return Collections.unmodifiableList(parameters);
    }

    public Class<?>[] getParameterTypesAsArray() {
        return slim.getJavaMember().getParameterTypes();
    }

    public List<EnhancedAnnotatedParameter<?, X>> getEnhancedParameters(Class<? extends Annotation> annotationType) {
        List<EnhancedAnnotatedParameter<?, X>> ret = new ArrayList<EnhancedAnnotatedParameter<?, X>>();
        for (EnhancedAnnotatedParameter<?, X> parameter : parameters) {
            if (parameter.isAnnotationPresent(annotationType)) {
                ret.add(parameter);
            }
        }
        return ret;
    }

    public boolean isEquivalent(Method method) {
        return this.getDeclaringType().isEquivalent(method.getDeclaringClass()) && this.getName().equals(method.getName()) && Arrays.equals(this.getParameterTypesAsArray(), method.getParameterTypes());
    }


    public T invokeOnInstance(Object instance, Object... parameters) throws IllegalArgumentException, SecurityException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        final Map<Class<?>, Method> methods = this.methods;
        Method method = methods.get(instance.getClass());
        if (method == null) {
            //the same method may be written to the map twice, but that is ok
            //lookupMethod is very slow
            method = SecureReflections.lookupMethod(instance.getClass(), getName(), getParameterTypesAsArray());
            synchronized (this) {
                final Map<Class<?>, Method> newMethods = new HashMap<Class<?>, Method>(methods);
                newMethods.put(instance.getClass(), method);
                this.methods = Collections.unmodifiableMap(newMethods);
            }
        }
        return SecureReflections.<T>invoke(instance, method, parameters);
    }

    public T invoke(Object instance, Object... parameters) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        return SecureReflections.<T>invoke(instance, slim.getJavaMember(), parameters);
    }

    public String getPropertyName() {
        return propertyName;
    }

    @Override
    public String toString() {
        return Formats.formatAnnotatedMethod(this);
    }

    public MethodSignature getSignature() {
        return signature;
    }

    public List<AnnotatedParameter<X>> getParameters() {
        return Collections.unmodifiableList(Reflections.<List<AnnotatedParameter<X>>>cast(parameters));
    }

    public boolean isGeneric() {
        return getJavaMember().getTypeParameters().length > 0;
    }

    @Override
    public AnnotatedMethod<X> slim() {
        return slim;
        }
}