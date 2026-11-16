/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.jsftemplating.el;

import jakarta.el.ELClass;
import jakarta.el.ELContext;
import jakarta.el.ELResolver;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Member;
import java.util.Set;

/**
 * {@link ELResolver} that blocks EL from reaching the Java runtime facilities
 * usable for sandbox escape / RCE (GlassFish #707).
 *
 * <p>Denies, across value- and method-expressions: static/constructor access
 * via {@link ELClass}; any operation on a sensitive base ({@link Class},
 * {@link ClassLoader}, {@code java.lang.reflect} members, {@link Runtime},
 * {@link Process}, {@link ProcessBuilder}, {@link Thread}); and the accessors
 * that hand such an object out from a benign base ({@code getClass},
 * {@code getClassLoader}, the {@code class}/{@code classLoader} properties).
 * The type guard is the backstop: once a sensitive object is held every
 * further hop off it is denied, so the name list only needs the pivots that
 * start from a benign base. Benign bean/handler navigation is left unresolved
 * for the rest of the chain.
 *
 * @author avpinchuk
 */
public class RestrictedELResolver extends ELResolver {

    // Sensitive base types; isInstance() so subtypes/impls are covered.
    private static final Set<Class<?>> DISALLOWED_BASE_TYPES = Set.of(
            Class.class, ClassLoader.class,
            AccessibleObject.class, Member.class,   // Method, Field, Constructor
            Runtime.class, Process.class, ProcessBuilder.class, Thread.class);

    // Accessors that hand out a sensitive object from an otherwise benign base.
    private static final Set<String> DISALLOWED_METHODS = Set.of(
            "getClass", "getClassLoader", "getContextClassLoader",
            "loadClass", "forName", "getRuntime", "exec");

    private static final Set<String> DISALLOWED_PROPERTIES = Set.of(
            "class", "classLoader", "contextClassLoader");

    private static boolean isDisallowedBase(Object base) {
        if (base instanceof ELClass) {
            return true;
        }
        for (Class<?> type : DISALLOWED_BASE_TYPES) {
            if (type.isInstance(base)) {
                return true;
            }
        }
        return false;
    }

    private static boolean deniesProperty(Object base, Object property) {
        return isDisallowedBase(base)
                || (property != null && DISALLOWED_PROPERTIES.contains(property.toString()));
    }

    @Override
    public Object getValue(ELContext elContext, Object base, Object property) {
        if (base == null) {
            return null;
        }
        if (deniesProperty(base, property)) {
            elContext.setPropertyResolved(true);
            throw new PropertyNotAllowedException();
        }
        return null;
    }

    @Override
    public Object invoke(ELContext elContext, Object base, Object method, Class<?>[] paramTypes, Object[] params) {
        if (base == null) {
            return null;
        }
        if (isDisallowedBase(base)
                || (method != null && DISALLOWED_METHODS.contains(method.toString()))) {
            elContext.setPropertyResolved(true);
            throw new MethodNotAllowedException();
        }
        return null;
    }

    @Override
    public Class<?> getType(ELContext elContext, Object base, Object property) {
        if (base == null) {
            return null;
        }
        if (deniesProperty(base, property)) {
            elContext.setPropertyResolved(true);
            throw new PropertyNotAllowedException();
        }
        return null;
    }

    @Override
    public void setValue(ELContext elContext, Object base, Object property, Object value) {
        if (base != null && deniesProperty(base, property)) {
            elContext.setPropertyResolved(true);
            throw new PropertyNotAllowedException();
        }
    }

    @Override
    public boolean isReadOnly(ELContext elContext, Object base, Object property) {
        return false;
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext elContext, Object base) {
        return null;
    }
}
