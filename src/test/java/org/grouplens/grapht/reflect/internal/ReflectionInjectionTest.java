/*
 * Grapht, an open source dependency injector.
 * Copyright 2014-2015 various contributors (see CONTRIBUTORS.txt)
 * Copyright 2010-2014 Regents of the University of Minnesota
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.grouplens.grapht.reflect.internal;

import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import org.grouplens.grapht.*;
import org.grouplens.grapht.BindingFunctionBuilder.RuleSet;
import org.grouplens.grapht.graph.DAGEdge;
import org.grouplens.grapht.graph.DAGNode;
import org.grouplens.grapht.reflect.Desire;
import org.grouplens.grapht.reflect.Desires;
import org.grouplens.grapht.reflect.InjectionPoint;
import org.grouplens.grapht.reflect.internal.types.*;
import org.grouplens.grapht.solver.DefaultDesireBindingFunction;
import org.grouplens.grapht.solver.DefaultInjector;
import org.junit.Assert;
import org.junit.Test;

import javax.inject.Provider;
import java.util.Map;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

public class ReflectionInjectionTest {
    @Test
    public void testProviderCycleInjection() throws Exception {
        InjectorBuilder b = InjectorBuilder.create().setProviderInjectionEnabled(true);
        Injector i = b.build();
        
        i.getInstance(CycleA.class);
        DAGNode<Component,Dependency> root = ((DefaultInjector) i).getSolver().getGraph();

        assertThat(root.getReachableNodes(), hasSize(3 + 1));

        assertThat(root.getOutgoingEdges(), hasSize(1));
        DAGNode<Component, Dependency> anode = root.getOutgoingEdges().iterator().next().getTail();
        Assert.assertEquals(CycleA.class, anode.getLabel().getSatisfaction().getErasedType());
        
        Assert.assertEquals(1, anode.getOutgoingEdges().size());
        DAGNode<Component, Dependency> bnode = anode.getOutgoingEdges().iterator().next().getTail();
        Assert.assertEquals(CycleB.class, bnode.getLabel().getSatisfaction().getErasedType());
        
        Assert.assertEquals(1, bnode.getOutgoingEdges().size());
        DAGNode<Component, Dependency> pnode = bnode.getOutgoingEdges().iterator().next().getTail();
        Assert.assertEquals(Provider.class, pnode.getLabel().getSatisfaction().getErasedType());

        // no outgoing edges...
        Assert.assertEquals(0, pnode.getOutgoingEdges().size());
        // but a back edge
        SetMultimap<DAGNode<Component,Dependency>,DAGEdge<Component, Dependency>> backEdges = ((DefaultInjector) i).getSolver().getBackEdges();
        assertThat(backEdges.entries(), hasSize(1));
        DAGEdge<Component, Dependency> edge = backEdges.values().iterator().next();
        Assert.assertSame(anode, edge.getTail());
    }
    
    @Test
    public void testTypeCInjectionWithDefaults() throws Exception {
        // Test that TypeC can be resolved successfully without any bind rules.
        // All of TypeC's dependencies have defaults or are satisfiable.
        Desire rootDesire = Desires.create(null, TypeC.class, false);
        DefaultInjector r = new DefaultInjector(DefaultDesireBindingFunction.create());
        
        TypeC instance = r.getInstance(TypeC.class);
        Assert.assertEquals(5, instance.getIntValue());
        Assert.assertNotNull(instance.getInterfaceA());
        Assert.assertTrue(instance.getInterfaceA() instanceof TypeB); // ProviderA actually creates TypeB's
        Assert.assertSame(instance.getInterfaceA(), instance.getTypeA());
        Assert.assertNotNull(instance.getInterfaceB());
        Assert.assertTrue(instance.getInterfaceB() instanceof TypeB);
        Assert.assertSame(instance.getInterfaceB(), instance.getTypeB());
        
        // also verify memoization
        Assert.assertSame(instance, r.getInstance(TypeC.class));

        DAGNode<Component, Dependency> resolvedRoot =
                r.getSolver().getGraph().getOutgoingEdgeWithLabel(Dependency.hasInitialDesire(rootDesire)).getTail();
        assertThat(resolvedRoot.getOutgoingEdges(),
                   hasSize(5));

        Map<InjectionPoint, DAGNode<Component, Dependency>> deps = Maps.newHashMap();
        for (DAGEdge<Component, Dependency> e: resolvedRoot.getOutgoingEdges()) {
            ReflectionDesire d = (ReflectionDesire) e.getLabel().getInitialDesire();
            
            if (d.getInjectionPoint().equals(TypeC.CONSTRUCTOR)) {
                // CycleA ParameterA defaults to 5
                Assert.assertFalse(deps.containsKey(TypeC.CONSTRUCTOR));
                Assert.assertTrue(e.getTail().getLabel().getSatisfaction() instanceof InstanceSatisfaction);
                Assert.assertEquals(5, ((InstanceSatisfaction) e.getTail().getLabel().getSatisfaction()).getInstance());
                deps.put(TypeC.CONSTRUCTOR, e.getTail());
            } else if (d.getInjectionPoint().equals(TypeC.INTERFACE_A)) {
                // An InterfaceA is implemented by TypeA, which is then provided by Provider CycleA
                Assert.assertFalse(deps.containsKey(TypeC.INTERFACE_A));
                Assert.assertTrue(e.getTail().getLabel().getSatisfaction() instanceof ProviderClassSatisfaction);
                Assert.assertEquals(ProviderA.class, ((ProviderClassSatisfaction) e.getTail().getLabel().getSatisfaction()).getProviderType());
                deps.put(TypeC.INTERFACE_A, e.getTail());
            } else if (d.getInjectionPoint().equals(TypeC.TYPE_A)) {
                // CycleA TypeA is provided by a ProviderA
                Assert.assertFalse(deps.containsKey(TypeC.TYPE_A));
                Assert.assertTrue(e.getTail().getLabel().getSatisfaction() instanceof ProviderClassSatisfaction);
                Assert.assertEquals(ProviderA.class, ((ProviderClassSatisfaction) e.getTail().getLabel().getSatisfaction()).getProviderType());
                deps.put(TypeC.TYPE_A, e.getTail());
            } else if (d.getInjectionPoint().equals(TypeC.INTERFACE_B)) {
                // RoleE inherits RoleD and that defaults to TypeB
                Assert.assertFalse(deps.containsKey(TypeC.INTERFACE_B));
                Assert.assertTrue(e.getTail().getLabel().getSatisfaction() instanceof ClassSatisfaction);
                Assert.assertEquals(TypeB.class, e.getTail().getLabel().getSatisfaction().getErasedType());
                deps.put(TypeC.INTERFACE_B, e.getTail());
            } else if (d.getInjectionPoint().equals(TypeC.TYPE_B)) {
                // TypeB is satisfiable on its own
                Assert.assertFalse(deps.containsKey(TypeC.TYPE_B));
                Assert.assertTrue(e.getTail().getLabel().getSatisfaction() instanceof ClassSatisfaction);
                Assert.assertEquals(TypeB.class, e.getTail().getLabel().getSatisfaction().getErasedType());
                deps.put(TypeC.TYPE_B, e.getTail());
            } else {
                Assert.fail();
            }
        }
        
        // verify that all injection points were tested
        Assert.assertTrue(deps.containsKey(TypeC.CONSTRUCTOR));
        Assert.assertTrue(deps.containsKey(TypeC.TYPE_A));
        Assert.assertTrue(deps.containsKey(TypeC.INTERFACE_A));
        Assert.assertTrue(deps.containsKey(TypeC.TYPE_B));
        Assert.assertTrue(deps.containsKey(TypeC.INTERFACE_B));
        
        // make sure that nodes are shared where appropriate
        Assert.assertSame(deps.get(TypeC.INTERFACE_A), deps.get(TypeC.TYPE_A));
        Assert.assertSame(deps.get(TypeC.INTERFACE_B), deps.get(TypeC.TYPE_B));
    }
    
    @Test
    public void testTypeCInjectionWithBindings() throws Exception {
        // Test that TypeC can be injected correctly using bind rules, although
        // the bind rule configuration does not need to be very complicated, since
        // the resolver and bind rules are already tested.
        Desire rootDesire = Desires.create(null, TypeC.class, false);
        
        TypeA a = new TypeA();
        TypeB b = new TypeB();
        
        BindingFunctionBuilder bindRules = new BindingFunctionBuilder(false);
        bindRules.getRootContext().bind(Integer.class).withQualifier(ParameterA.class).to(10);
        bindRules.getRootContext().bind(InterfaceA.class).withQualifier(RoleA.class).to(PrimeA.class);
        bindRules.getRootContext().bind(InterfaceB.class).withQualifier(RoleD.class).to(PrimeB.class);
        bindRules.getRootContext().bind(TypeA.class).to(a);
        bindRules.getRootContext().bind(TypeB.class).to(b);
        
        DefaultInjector r = new DefaultInjector(bindRules.build(RuleSet.EXPLICIT), DefaultDesireBindingFunction.create());

        TypeC instance = r.getInstance(TypeC.class);
        Assert.assertEquals(10, instance.getIntValue());
        Assert.assertNotNull(instance.getInterfaceA());
        Assert.assertTrue(instance.getInterfaceA() instanceof PrimeA);
        Assert.assertSame(a, instance.getTypeA());
        Assert.assertNotNull(instance.getInterfaceB());
        Assert.assertTrue(instance.getInterfaceB() instanceof PrimeB);
        Assert.assertSame(b, instance.getTypeB());
        
        // also verify memoization
        Assert.assertSame(instance, r.getInstance(TypeC.class));

        DAGNode<Component, Dependency> resolvedRoot =
                r.getSolver().getGraph().getOutgoingEdgeWithLabel(Dependency.hasInitialDesire(rootDesire)).getTail();
        assertThat(resolvedRoot.getOutgoingEdges(),
                   hasSize(5));
        
        Map<InjectionPoint, DAGNode<Component, Dependency>> deps = Maps.newHashMap();
        for (DAGEdge<Component, Dependency> e: resolvedRoot.getOutgoingEdges()) {
            ReflectionDesire d = (ReflectionDesire) e.getLabel().getInitialDesire();
            
            if (d.getInjectionPoint().equals(TypeC.CONSTRUCTOR)) {
                // ParameterA was set to 10
                Assert.assertFalse(deps.containsKey(TypeC.CONSTRUCTOR));
                Assert.assertTrue(e.getTail().getLabel().getSatisfaction() instanceof InstanceSatisfaction);
                Assert.assertEquals(10, ((InstanceSatisfaction) e.getTail().getLabel().getSatisfaction()).getInstance());
                deps.put(TypeC.CONSTRUCTOR, e.getTail());
            } else if (d.getInjectionPoint().equals(TypeC.INTERFACE_A)) {
                // An InterfaceA has been bound to PrimeA
                Assert.assertFalse(deps.containsKey(TypeC.INTERFACE_A));
                Assert.assertTrue(e.getTail().getLabel().getSatisfaction() instanceof ClassSatisfaction);
                Assert.assertEquals(PrimeA.class, e.getTail().getLabel().getSatisfaction().getErasedType());
                deps.put(TypeC.INTERFACE_A, e.getTail());
            } else if (d.getInjectionPoint().equals(TypeC.TYPE_A)) {
                // CycleA TypeA has been bound to an instance
                Assert.assertFalse(deps.containsKey(TypeC.TYPE_A));
                Assert.assertTrue(e.getTail().getLabel().getSatisfaction() instanceof InstanceSatisfaction);
                Assert.assertSame(a, ((InstanceSatisfaction) e.getTail().getLabel().getSatisfaction()).getInstance());
                deps.put(TypeC.TYPE_A, e.getTail());
            } else if (d.getInjectionPoint().equals(TypeC.INTERFACE_B)) {
                // RoleE has been bound to PrimeB
                Assert.assertFalse(deps.containsKey(TypeC.INTERFACE_B));
                Assert.assertTrue(e.getTail().getLabel().getSatisfaction() instanceof ClassSatisfaction);
                Assert.assertEquals(PrimeB.class, e.getTail().getLabel().getSatisfaction().getErasedType());
                deps.put(TypeC.INTERFACE_B, e.getTail());
            } else if (d.getInjectionPoint().equals(TypeC.TYPE_B)) {
                // TypeB has been bound to an instance
                Assert.assertFalse(deps.containsKey(TypeC.TYPE_B));
                Assert.assertTrue(e.getTail().getLabel().getSatisfaction() instanceof InstanceSatisfaction);
                Assert.assertSame(b, ((InstanceSatisfaction) e.getTail().getLabel().getSatisfaction()).getInstance());
                deps.put(TypeC.TYPE_B, e.getTail());
            } else {
                Assert.fail();
            }
        }
        
        // verify that all injection points were tested
        Assert.assertTrue(deps.containsKey(TypeC.CONSTRUCTOR));
        Assert.assertTrue(deps.containsKey(TypeC.TYPE_A));
        Assert.assertTrue(deps.containsKey(TypeC.INTERFACE_A));
        Assert.assertTrue(deps.containsKey(TypeC.TYPE_B));
        Assert.assertTrue(deps.containsKey(TypeC.INTERFACE_B));
    }
    
    public static class PrimeA implements InterfaceA {
        
    }
    
    public static class PrimeB implements InterfaceB {
        
    }
}
