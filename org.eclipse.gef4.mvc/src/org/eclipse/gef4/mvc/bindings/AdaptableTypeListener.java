/*******************************************************************************
 * Copyright (c) 2014 itemis AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Alexander Nyßen (itemis AG) - initial API and implementation
 *     
 *******************************************************************************/
package org.eclipse.gef4.mvc.bindings;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.MembersInjector;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinderBinding;
import com.google.inject.multibindings.MultibinderBinding;
import com.google.inject.multibindings.MultibindingsTargetVisitor;
import com.google.inject.spi.ConstructorBinding;
import com.google.inject.spi.ConvertedConstantBinding;
import com.google.inject.spi.ExposedBinding;
import com.google.inject.spi.InstanceBinding;
import com.google.inject.spi.LinkedKeyBinding;
import com.google.inject.spi.ProviderBinding;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderKeyBinding;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import com.google.inject.spi.UntargettedBinding;

/**
 * A specific {@link TypeListener} to support adaptable member injection.
 * 
 * @author anyssen
 *
 */
public class AdaptableTypeListener implements TypeListener {

	// the injector used to obtain adapter map bindings
	private Injector injector;

	// used to keep track of members that are to be injected before we have
	// obtained the injector (bug #439949)
	private Map<AdaptableMemberInjector<?>, List<?>> deferredInjections = new HashMap<AdaptableMemberInjector<?>, List<?>>();

	public class AdaptableMemberInjector<T> implements MembersInjector<T> {

		private class AdapterBindingsTargetVisitor implements
				MultibindingsTargetVisitor<Object, Map<AdapterKey<?>, Object>> {
			@Override
			public Map<AdapterKey<?>, Object> visit(
					InstanceBinding<? extends Object> binding) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Map<AdapterKey<?>, Object> visit(
					ProviderInstanceBinding<? extends Object> binding) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Map<AdapterKey<?>, Object> visit(
					ProviderKeyBinding<? extends Object> binding) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Map<AdapterKey<?>, Object> visit(
					LinkedKeyBinding<? extends Object> binding) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Map<AdapterKey<?>, Object> visit(
					ExposedBinding<? extends Object> binding) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Map<AdapterKey<?>, Object> visit(
					UntargettedBinding<? extends Object> binding) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Map<AdapterKey<?>, Object> visit(
					ConstructorBinding<? extends Object> binding) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Map<AdapterKey<?>, Object> visit(
					ConvertedConstantBinding<? extends Object> binding) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Map<AdapterKey<?>, Object> visit(
					ProviderBinding<? extends Object> binding) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Map<AdapterKey<?>, Object> visit(
					MultibinderBinding<? extends Object> multibinding) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Map<AdapterKey<?>, Object> visit(
					MapBinderBinding<? extends Object> mapbinding) {
				Map<AdapterKey<?>, Object> bindings = new HashMap<AdapterKey<?>, Object>();
				for (Entry<?, Binding<?>> entry : mapbinding.getEntries()) {
					bindings.put((AdapterKey<?>) entry.getKey(), entry
							.getValue().getProvider().get());
				}
				return bindings;
			}
		}

		private Method method;
		private AdapterMap methodAnnotation;

		public AdaptableMemberInjector(Method method,
				AdapterMap methodAnnotation) {
			this.method = method;
			this.methodAnnotation = methodAnnotation;
		}

		protected SortedMap<Key<?>, Binding<?>> getPolymorphicAdapterBindingKeys(
				Class<?> type, Method method, AdapterMap methodAnnotation) {
			// find available keys
			Map<Key<?>, Binding<?>> allBindings = injector.getAllBindings();
			// IMPORTANT: use a sorted map, where keys are sorted according to
			// hierarchy of annotation types (so we have polymorphic injection)
			SortedMap<Key<?>, Binding<?>> polymorphicBindings = new TreeMap<Key<?>, Binding<?>>(
					new Comparator<Key<?>>() {

						@Override
						public int compare(Key<?> o1, Key<?> o2) {
							if (!AdapterMap.class.equals(o1.getAnnotationType())
									|| !AdapterMap.class.equals(o2
											.getAnnotationType())) {
								throw new IllegalArgumentException(
										"Can only compare keys with AdapterMap annotations");
							}
							AdapterMap a1 = (AdapterMap) o1.getAnnotation();
							AdapterMap a2 = (AdapterMap) o2.getAnnotation();
							if (a1.value().equals(a2.value())) {
								return 0;
							} else if (a1.value().isAssignableFrom(a2.value())) {
								return -1;
							} else {
								return 1;
							}
						}
					});
			for (Key<?> key : allBindings.keySet()) {
				if (key.getAnnotationType() != null
						&& AdapterMap.class.equals(key.getAnnotationType())) {
					AdapterMap keyAnnotation = (AdapterMap) key.getAnnotation();
					// Guice will already have injected all
					// bindings for those annotations using the exact
					// class, so we will only deal with subclasses here
					if (methodAnnotation.value().isAssignableFrom(
							keyAnnotation.value())
							&& !methodAnnotation.value().equals(
									keyAnnotation.value())
							/*
							 * key annotation refers to a true subtype of method
							 * annotation (check, because if the type is the
							 * same, the default injector will already inject
							 * the values)
							 */
							&& keyAnnotation.value().isAssignableFrom(type)) {
						// System.out.println("Applying binding for " +
						// keyAnnotation.value() + " to " + type +
						// " as subtype of " + methodAnnotation.value());
						polymorphicBindings.put(key, allBindings.get(key));
					}
				}
			}
			return polymorphicBindings;
		}

		@Override
		public void injectMembers(T instance) {
			// inject all adapters bound by polymorphic AdapterBinding
			// annotations
			if (injector == null) {
				// IMPORTANT: it may happen that this member injector is
				// exercised before the type listener is injected. In such a
				// case we need to defer the injections until the injector is
				// available (bug #439949).
				@SuppressWarnings("unchecked")
				List<T> deferredInstances = (List<T>) deferredInjections
						.get(this);
				if (deferredInstances == null) {
					deferredInstances = new ArrayList<T>();
					deferredInjections.put(this, deferredInstances);
				}
				deferredInstances.add(instance);
			} else {
				injectAdapters(instance);
			}
		}

		protected void injectAdapters(Object instance) {
			SortedMap<Key<?>, Binding<?>> polymorphicBindings = getPolymorphicAdapterBindingKeys(
					instance.getClass(), method, methodAnnotation);
			// System.out.println("--");
			for (Map.Entry<Key<?>, Binding<?>> entry : polymorphicBindings
					.entrySet()) {
				// System.out.println(((AdapterMap)entry.getKey().getAnnotation()).value());
				try {
					Map<AdapterKey<?>, Object> target = entry.getValue()
							.acceptTargetVisitor(
									new AdapterBindingsTargetVisitor());
					if (target != null && !target.isEmpty()) {
						// System.out.println("Injecting " + method.getName()
						// + " of " + instance + " with " + target
						// + " based on " + entry.getValue());
						method.invoke(instance, target);
					}
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				} catch (InvocationTargetException e) {
					e.printStackTrace();
				}
			}
		}

	}

	@Inject
	public void setInjector(Injector injector) {
		this.injector = injector;
		// perform deferred injections (if there have been any)
		for (AdaptableMemberInjector<?> memberInjector : deferredInjections
				.keySet()) {
			for (Object instance : deferredInjections.get(memberInjector)) {
				memberInjector.injectAdapters(instance);
			}
		}
		deferredInjections.clear();
	}

	@Override
	public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
		if (IAdaptable.class.isAssignableFrom(type.getRawType())) {
			for (final Method method : type.getRawType().getMethods()) {
				for (int i = 0; i < method.getParameterAnnotations().length; i++) {
					AdapterMap methodAnnotation = getAnnotation(
							method.getParameterAnnotations()[i],
							AdapterMap.class);
					// we have a method annotated with AdapterBinding
					if (methodAnnotation != null) {
						if (method.getParameterTypes().length != 1) {
							encounter
									.addError(
											"AdapterBinding annotation is only valid on one-parameter operations.",
											method);
						}
						// TODO: check parameter type is appropriate
						// System.out.println("Registering member injector to "
						// + type);
						encounter.register(new AdaptableMemberInjector<I>(
								method, methodAnnotation));
					}
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private <T extends Annotation> T getAnnotation(Annotation[] annotations,
			Class<T> annotationType) {
		for (Annotation a : annotations) {
			if (annotationType.isAssignableFrom(a.annotationType())) {
				return (T) a;
			}
		}
		return null;
	}

}
