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

package com.google.inject.spi;

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.inject.Key;
import com.google.inject.internal.MoreTypes;
import com.google.inject.internal.ToStringBuilder;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;

/**
 * An immutable snapshot of where the value is to be injected.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public final class InjectionPoint<T> implements Serializable {

  private final Member member;
  private final Key<T> key;
  private final int parameterIndex;
  private final boolean allowsNull;

  private InjectionPoint(Member member, int paramterIndex,
      boolean allowsNull, Key<T> key) {
    this.member = member;
    this.parameterIndex = paramterIndex;
    this.allowsNull = allowsNull;
    this.key = checkNotNull(key, "key");
  }

  public Key<T> getKey() {
    return this.key;
  }

  public Member getMember() {
    return member;
  }

  public int getParameterIndex() {
    return parameterIndex;
  }

  public boolean allowsNull() {
    return allowsNull;
  }

  @Override public String toString() {
    ToStringBuilder builder = new ToStringBuilder(InjectionPoint.class)
        .add("key", key)
        .add("allowsNull", allowsNull);

    if (member != null) {
      builder.add("member", MoreTypes.toString(member));
    }
    if (parameterIndex != -1) {
      builder.add("parameterIndex", parameterIndex);
    }
    return builder.toString();
  }

  public static <T> InjectionPoint<T> newInstance(
      Field field, boolean allowsNull, Key<T> key) {
    return new InjectionPoint<T>(field, -1, allowsNull, key);
  }

  public static <T> InjectionPoint<T> newInstance(Key<T> key) {
    return new InjectionPoint<T>(null, -1, true, key);
  }

  public static <T> InjectionPoint<T> newInstance(Member member, int parameterIndex,
      boolean allowsNull, Key<T> key) {
    return new InjectionPoint<T>(member, parameterIndex, allowsNull, key);
  }

  private Object writeReplace() throws ObjectStreamException {
    Member serializableMember = member != null ? MoreTypes.serializableCopy(member) : null;
    return new InjectionPoint<T>(serializableMember, parameterIndex, allowsNull, key);
  }
}
