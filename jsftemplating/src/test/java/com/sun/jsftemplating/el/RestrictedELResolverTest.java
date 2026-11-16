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
import jakarta.el.FunctionMapper;
import jakarta.el.VariableMapper;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Verifies {@link RestrictedELResolver} severs the #707 reflection/class-loader
 * escape chain while leaving ordinary bean navigation untouched. Non-destructive:
 * it asserts only that the dangerous hops are refused.
 */
public class RestrictedELResolverTest {

    private RestrictedELResolver resolver;
    private ELContext context;

    private static final class StubELContext extends ELContext {
        @Override public ELResolver getELResolver() { return null; }
        @Override public FunctionMapper getFunctionMapper() { return null; }
        @Override public VariableMapper getVariableMapper() { return null; }
    }

    @Before
    public void setUp() {
        resolver = new RestrictedELResolver();
        context = new StubELContext();
    }

    private void assertInvokeDenied(Object base, String method) {
        context.setPropertyResolved(false);
        try {
            resolver.invoke(context, base, method, null, new Object[0]);
            fail("expected denial for base=" + base + ", method=" + method);
        } catch (MethodNotAllowedException expected) {
            assertTrue(context.isPropertyResolved());
        }
    }

    private void assertPropertyDenied(Object base, String property) {
        context.setPropertyResolved(false);
        try {
            resolver.getValue(context, base, property);
            fail("expected denial for base=" + base + ", property=" + property);
        } catch (PropertyNotAllowedException expected) {
            assertTrue(context.isPropertyResolved());
        }
    }

    @Test
    public void deniesPivotMethodNames() {
        for (String m : new String[] {
                "getClass", "getClassLoader", "getContextClassLoader",
                "loadClass", "forName", "getRuntime", "exec" }) {
            assertInvokeDenied(new Object(), m);
        }
    }

    @Test
    public void deniesAnyMethodOnSensitiveBase() {
        assertInvokeDenied(ClassLoader.getSystemClassLoader(), "toString");
        assertInvokeDenied(Thread.currentThread(), "interrupt");
        assertInvokeDenied(String.class, "getName");
        assertInvokeDenied(new ELClass(Runtime.class), "getRuntime");
    }

    @Test
    public void deniesSensitiveProperties() {
        for (String p : new String[] { "class", "classLoader", "contextClassLoader" }) {
            assertPropertyDenied(new Object(), p);
        }
        assertPropertyDenied(ClassLoader.getSystemClassLoader(), "parent");
    }

    @Test
    public void deniesGetTypeOnSensitiveBase() {
        context.setPropertyResolved(false);
        try {
            resolver.getType(context, String.class, "name");
            fail("expected denial from getType on a Class base");
        } catch (PropertyNotAllowedException expected) {
            assertTrue(context.isPropertyResolved());
        }
    }

    @Test
    public void nullNamePassesThrough() {
        context.setPropertyResolved(false);
        assertNull(resolver.getValue(context, new Bean(), null));
        assertNull(resolver.invoke(context, new Bean(), null, null, new Object[0]));
        assertFalse(context.isPropertyResolved());
    }

    public static final class Bean {
        public String getName() { return "ok"; }
    }

    @Test
    public void allowsBenignPropertyRead() {
        context.setPropertyResolved(false);
        assertNull(resolver.getValue(context, new Bean(), "name"));
        assertFalse(context.isPropertyResolved());
    }

    @Test
    public void allowsBenignMethodCall() {
        context.setPropertyResolved(false);
        assertNull(resolver.invoke(context, new Bean(), "getName", new Class<?>[0], new Object[0]));
        assertFalse(context.isPropertyResolved());
    }
}
