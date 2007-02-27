/**
 * Copyright (C) 2006 Google Inc.
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

package com.google.inject;

/**
 * A scope is a level of visibility that instances provided by Guice may have.
 * By default, instances created by the Guice container have <i>no scope</i>,
 * meaning they have no visibility -- guice creates them, injects them once,
 * then immediately forgets them.  Associating a scope with a particular binding
 * allows the created instance to be "remembered" and possibly used again for
 * other injections.
 *
 * @see Scopes#CONTAINER
 *
 * @author crazybob@google.com (Bob Lee)
 */
public interface Scope {

  /**
   * Scopes a provider. The returned locator returns objects from this scope. If
   * an object does not exist in this scope, the provider can use the given
   * unscoped provider to retrieve one.
   *
   * <p>Scope implementations are strongly encouraged to override
   * {@link Object#toString} in the returned provider and include the backing
   * provider's {@code toString()} output.
   *
   * @param key binding key
   * @param unscoped locates an instance when one doesn't already exist in this
   *  scope.
   * @return a new provider which only delegates to the given unscoped provider
   *  when an instance of the requested object doesn't already exist in this
   *  scope
   */
  public <T> Provider<T> scope(Key<T> key, Provider<T> unscoped);

  /**
   * A short but useful description of this scope.  For comparison, the standard
   * scopes that ship with guice use the descriptions {@code "Scopes.CONTAINER"},
   * {@code "ServletScopes.SESSION"} and {@code "ServletScopes.REQUEST"}.
   */
  String toString();
}
