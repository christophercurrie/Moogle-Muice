/**
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject.assistedinject;

import static com.google.common.base.Preconditions.checkState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import static com.google.common.collect.Iterables.getOnlyElement;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.ConfigurationException;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.TypeLiteral;
import static com.google.inject.internal.Annotations.getKey;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.spi.Message;
import com.google.inject.util.Providers;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Arrays;


/**
 * The newer implementation of factory provider. This implementation uses a child injector to
 * create values.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @author dtm@google.com (Daniel Martin)
 */
final class FactoryProvider2<F> implements InvocationHandler, Provider<F> {

  /** if a factory method parameter isn't annotated, it gets this annotation. */
  static final Assisted DEFAULT_ANNOTATION = new Assisted() {
    public String value() {
      return "";
    }

    public Class<? extends Annotation> annotationType() {
      return Assisted.class;
    }

    @Override public boolean equals(Object o) {
      return o instanceof Assisted
          && ((Assisted) o).value().equals("");
    }

    @Override public int hashCode() {
      return 127 * "value".hashCode() ^ "".hashCode();
    }

    @Override public String toString() {
      return "@" + Assisted.class.getName() + "(value=)";
    }
  };

  /** the produced type, or null if all methods return concrete types */
  private final Key<?> producedType;
  private final ImmutableMap<Method, Key<?>> returnTypesByMethod;
  private final ImmutableMultimap<Method, Key<?>> paramTypes;

  /** the hosting injector, or null if we haven't been initialized yet */
  private Injector injector;

  /** the factory interface, implemented and provided */
  private final F factory;

  /**
   * @param factoryType a Java interface that defines one or more create methods.
   * @param producedType a concrete type that is assignable to the return types of all factory
   *      methods.
   */
  FactoryProvider2(Class<F> factoryType, Key<?> producedType) {
    this.producedType = producedType;

    Errors errors = new Errors();
    try {
      ImmutableMap.Builder<Method, Key<?>> returnTypesBuilder = ImmutableMap.builder();
      ImmutableMultimap.Builder<Method, Key<?>> paramTypesBuilder = ImmutableMultimap.builder();
      // TODO: also grab methods from superinterfaces
      for (Method method : factoryType.getMethods()) {
        Key<?> returnType = getKey(TypeLiteral.get(method.getGenericReturnType()),
            method, method.getAnnotations(), errors);
        returnTypesBuilder.put(method, returnType);
        Type[] params = method.getGenericParameterTypes();
        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        int p = 0;
        for (Type param : params) {
          Key<?> paramKey = getKey(TypeLiteral.get(param), method, paramAnnotations[p++], errors);
          paramTypesBuilder.put(method, assistKey(method, paramKey, errors));
        }
      }
      returnTypesByMethod = returnTypesBuilder.build();
      paramTypes = paramTypesBuilder.build();
    } catch (ErrorsException e) {
      throw new ConfigurationException(e.getErrors().getMessages());
    }

    factory = factoryType.cast(Proxy.newProxyInstance(factoryType.getClassLoader(),
        new Class[] { factoryType }, this));
  }

  public F get() {
    return factory;
  }

  /**
   * Returns a key similar to {@code key}, but with an {@literal @}Assisted binding annotation.
   * This fails if another binding annotation is clobbered in the process. If the key already has
   * the {@literal @}Assisted annotation, it is returned as-is to preserve any String value.
   */
  private <T> Key<T> assistKey(Method method, Key<T> key, Errors errors) throws ErrorsException {
    if (key.getAnnotationType() == null) {
      return Key.get(key.getTypeLiteral(), DEFAULT_ANNOTATION);
    } else if (key.getAnnotationType() == Assisted.class) {
      return key;
    } else {
      errors.withSource(method).addMessage(
          "Only @Assisted is allowed for factory parameters, but found @%s",
          key.getAnnotationType());
      throw errors.toException();
    }
  }

  /**
   * At injector-creation time, we initialize the invocation handler. At this time we make sure
   * all factory methods will be able to build the target types.
   */
  @Inject
  void initialize(Injector injector) {
    if (this.injector != null) {
      throw new ConfigurationException(ImmutableList.of(new Message(FactoryProvider2.class,
          "Factories.create() factories may only be used in one Injector!")));
    }

    this.injector = injector;

    for (Method method : returnTypesByMethod.keySet()) {
      Object[] args = new Object[method.getParameterTypes().length];
      Arrays.fill(args, "dummy object for validating Factories");
      getBindingFromNewInjector(method, args); // throws if the binding isn't properly configured
    }
  }

  /**
   * Creates a child injector that binds the args, and returns the binding for the method's result.
   */
  public Binding<?> getBindingFromNewInjector(final Method method, final Object[] args) {
    checkState(injector != null,
        "Factories.create() factories cannot be used until they're initialized by Guice.");

    final Key<?> returnType = returnTypesByMethod.get(method);

    Module assistedModule = new AbstractModule() {
      @SuppressWarnings("unchecked") // raw keys are necessary for the args array and return value
      protected void configure() {
        Binder binder = binder().withSource(method);

        int p = 0;
        for (Key<?> paramKey : paramTypes.get(method)) {
          // Wrap in a Provider to cover null, and to prevent Guice from injecting the parameter
          binder.bind((Key) paramKey).toProvider(Providers.of(args[p++]));
        }

        if (producedType != null && !returnType.equals(producedType)) {
          binder.bind(returnType).to((Key) producedType);
        } else {
          binder.bind(returnType);
        }
      }
    };

    Injector forCreate = injector.createChildInjector(assistedModule);
    return forCreate.getBinding(returnType);
  }

  /**
   * When a factory method is invoked, we create a child injector that binds all parameters, then
   * use that to get an instance of the return type.
   */
  public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
    if (method.getDeclaringClass() == Object.class) {
      return method.invoke(this, args);
    }

    Provider<?> provider = getBindingFromNewInjector(method, args).getProvider();
    try {
      return provider.get();
    } catch (ProvisionException e) {
      // if this is an exception declared by the factory method, throw it as-is
      if (e.getErrorMessages().size() == 1) {
        Message onlyError = getOnlyElement(e.getErrorMessages());
        Throwable cause = onlyError.getCause();
        if (cause != null && canRethrow(method, cause)) {
          throw cause;
        }
      }
      throw e;
    }
  }

  @Override public String toString() {
    return factory.getClass().getInterfaces()[0].getName()
        + " for " + producedType.getTypeLiteral();
  }

  @Override public boolean equals(Object o) {
    return o == this || o == factory;
  }

  /** Returns true if {@code thrown} can be thrown by {@code invoked} without wrapping. */
  static boolean canRethrow(Method invoked, Throwable thrown) {
    if (thrown instanceof Error || thrown instanceof RuntimeException) {
      return true;
    }

    for (Class<?> declared : invoked.getExceptionTypes()) {
      if (declared.isInstance(thrown)) {
        return true;
      }
    }

    return false;
  }
}
