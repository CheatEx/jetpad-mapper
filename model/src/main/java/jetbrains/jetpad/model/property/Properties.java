/*
 * Copyright 2012-2013 JetBrains s.r.o
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jetbrains.jetpad.model.property;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import jetbrains.jetpad.model.collections.CollectionAdapter;
import jetbrains.jetpad.model.collections.CollectionItemEvent;
import jetbrains.jetpad.model.collections.CollectionListener;
import jetbrains.jetpad.model.collections.ObservableCollection;
import jetbrains.jetpad.model.collections.list.ObservableList;
import jetbrains.jetpad.model.event.EventHandler;
import jetbrains.jetpad.model.event.Registration;

public class Properties {
  public static ReadableProperty<Boolean> not(ReadableProperty<Boolean> prop) {
    return transform(prop, new Function<Boolean, Boolean>() {
      @Override
      public Boolean apply(Boolean s) {
        if (s == null) {
          s = false;
        }
        return !s;
      }
    });
  }

  public static <ValueT> ReadableProperty<Boolean> notNull(ReadableProperty<ValueT> prop) {
    return transform(prop, new Function<ValueT, Boolean>() {
      @Override
      public Boolean apply(ValueT v) {
        return v != null;
      }
    });
  }

  public static <ValueT> ReadableProperty<Boolean> isNull(ReadableProperty<ValueT> prop) {
    return transform(prop, new Function<ValueT, Boolean>() {
      @Override
      public Boolean apply(ValueT v) {
        return v == null;
      }
    });
  }

  public static ReadableProperty<Boolean> startsWith(final ReadableProperty<String> string, final ReadableProperty<String> prefix) {
    return new DerivedProperty<Boolean>(string, prefix) {
      @Override
      public Boolean get() {
        if (string.get() == null) return false;
        if (prefix.get() == null) return false;
        return string.get().startsWith(prefix.get());
      }

      @Override
      public String getPropExpr() {
        return "startsWith(" + string.getPropExpr() + ", " + prefix.getPropExpr() + ")";
      }
    };
  }

  public static ReadableProperty<Boolean> isEmptyString(final ReadableProperty<String> prop) {
    return new DerivedProperty<Boolean>(prop) {
      @Override
      public Boolean get() {
        String val = prop.get();
        return val == null || val.length() == 0;
      }

      @Override
      public String getPropExpr() {
        return "isEmptyString(" + prop.getPropExpr() + ")";
      }
    };
  }

  public static ReadableProperty<Boolean> and(final ReadableProperty<Boolean> op1, final ReadableProperty<Boolean> op2) {
    return new DerivedProperty<Boolean>(op1, op2) {
      @Override
      public Boolean get() {
        return op1.get() && op2.get();
      }

      @Override
      public String getPropExpr() {
        return "(" + op1.getPropExpr() + " && " + op2.getPropExpr() + ")";
      }
    };
  }

  public static ReadableProperty<Boolean> or(final ReadableProperty<Boolean> op1, final ReadableProperty<Boolean> op2) {
    return new DerivedProperty<Boolean>(op1, op2) {
      @Override
      public Boolean get() {
        return op1.get() || op2.get();
      }

      @Override
      public String getPropExpr() {
        return "(" + op1.getPropExpr() + " || " + op2.getPropExpr() + ")";
      }
    };
  }

  public static ReadableProperty<Integer> add(final ReadableProperty<Integer> p1, final ReadableProperty<Integer> p2) {
    return new DerivedProperty<Integer>(p1, p2) {
      @Override
      public Integer get() {
        if (p1.get() == null || p2.get() == null) return null;
        return p1.get() + p2.get();
      }

      @Override
      public String getPropExpr() {
        return "(" + p1.getPropExpr() + " + " + p2.getPropExpr() + ")";
      }
    };
  }

  public static <SourceT, TargetT> ReadableProperty<TargetT> select(final ReadableProperty<SourceT> source, final Selector<SourceT, ReadableProperty<TargetT>> fun) {
    final Supplier<TargetT> calc = new Supplier<TargetT>() {
      @Override
      public TargetT get() {
        SourceT value = source.get();
        if (value == null) return null;
        ReadableProperty<TargetT> prop = fun.select(value);
        if (prop == null) return null;
        return prop.get();
      }
    };

    return new BaseDerivedProperty<TargetT>(calc.get()) {
      private ReadableProperty<TargetT> myTargetProperty;

      private Registration mySourceRegistration;
      private Registration myTargetRegistration;

      @Override
      protected void doAddListeners() {
        myTargetProperty = source.get() == null ? null : fun.select(source.get());

        final EventHandler<PropertyChangeEvent<TargetT>> targetHandler = new EventHandler<PropertyChangeEvent<TargetT>>() {
          @Override
          public void onEvent(PropertyChangeEvent<TargetT> event) {
            somethingChanged();
          }
        };
        final EventHandler<PropertyChangeEvent<SourceT>> sourceHandler = new EventHandler<PropertyChangeEvent<SourceT>>() {
          @Override
          public void onEvent(PropertyChangeEvent<SourceT> event) {
            if (myTargetProperty != null) {
              myTargetRegistration.remove();
            }
            SourceT sourceValue = source.get();
            if (sourceValue != null) {
              myTargetProperty = fun.select(sourceValue);
            } else {
              myTargetProperty = null;
            }
            if (myTargetProperty != null) {
              myTargetRegistration = myTargetProperty.addHandler(targetHandler);
            }
            somethingChanged();
          }
        };
        mySourceRegistration = source.addHandler(sourceHandler);
        if (myTargetProperty != null) {
          myTargetRegistration = myTargetProperty.addHandler(targetHandler);
        }
      }

      @Override
      protected void doRemoveListeners() {
        if (myTargetProperty != null) {
          myTargetRegistration.remove();
        }
        mySourceRegistration.remove();
      }

      @Override
      public TargetT get() {
        return calc.get();
      }

      @Override
      public String getPropExpr() {
        return "select(" + source.getPropExpr() + ", " + fun +")";
      }
    };
  }

  public static <SourceT, TargetT> Property<TargetT> selectRw(final ReadableProperty<SourceT> source, final Selector<SourceT, Property<TargetT>> fun) {
    final Supplier<TargetT> calc = new Supplier<TargetT>() {
      @Override
      public TargetT get() {
        SourceT value = source.get();
        if (value == null) return null;
        ReadableProperty<TargetT> prop = fun.select(value);
        if (prop == null) return null;
        return prop.get();
      }
    };

    class MyProperty extends BaseDerivedProperty<TargetT> implements Property<TargetT> {
      private Property<TargetT> myTargetProperty;

      private Registration mySourceRegistration;
      private Registration myTargetRegistration;

      MyProperty() {
        super(calc.get());
      }

      @Override
      protected void doAddListeners() {
        myTargetProperty = source.get() == null ? null : fun.select(source.get());

        final EventHandler<PropertyChangeEvent<TargetT>> targetHandler = new EventHandler<PropertyChangeEvent<TargetT>>() {
          @Override
          public void onEvent(PropertyChangeEvent<TargetT> event) {
            somethingChanged();
          }
        };
        final EventHandler<PropertyChangeEvent<SourceT>> sourceHandler = new EventHandler<PropertyChangeEvent<SourceT>>() {
          @Override
          public void onEvent(PropertyChangeEvent<SourceT> event) {
            if (myTargetProperty != null) {
              myTargetRegistration.remove();
            }
            SourceT sourceValue = source.get();
            if (sourceValue != null) {
              myTargetProperty = fun.select(sourceValue);
            } else {
              myTargetProperty = null;
            }
            if (myTargetProperty != null) {
              myTargetRegistration = myTargetProperty.addHandler(targetHandler);
            }
            somethingChanged();
          }
        };
        mySourceRegistration = source.addHandler(sourceHandler);
        if (myTargetProperty != null) {
          myTargetRegistration = myTargetProperty.addHandler(targetHandler);
        }
      }

      @Override
      protected void doRemoveListeners() {
        if (myTargetProperty != null) {
          myTargetRegistration.remove();
        }
        mySourceRegistration.remove();
      }

      @Override
      public TargetT get() {
        return calc.get();
      }

      @Override
      public void set(TargetT value) {
        if (myTargetProperty == null) return;
        myTargetProperty.set(value);
      }

      @Override
      public String getPropExpr() {
        return "select(" + source.getPropExpr() + ", " + fun +")";
      }
    }

    return new MyProperty();
  }

  public static <ValueT> ReadableProperty<Boolean> same(final ReadableProperty<ValueT> prop, final ValueT value) {
    return transform(prop, new Function<ValueT, Boolean>() {
      @Override
      public Boolean apply(ValueT s) {
        return s == value;
      }
    });
  }

  public static <ValueT> ReadableProperty<Boolean> equals(final ReadableProperty<ValueT> prop, final ValueT value) {
    return transform(prop, new Function<ValueT, Boolean>() {
      @Override
      public Boolean apply(ValueT s) {
        return Objects.equal(value, s);
      }
    });
  }

  public static <ValueT> ReadableProperty<Boolean> equals(final ReadableProperty<? extends ValueT> p1, final ReadableProperty<? extends ValueT> p2) {
    return new DerivedProperty<Boolean>(p1, p2) {
      @Override
      public Boolean get() {
        return Objects.equal(p1.get(), p2.get());
      }

      @Override
      public String getPropExpr() {
        return "equals(" + p1.getPropExpr() + ", " + p2.getPropExpr() + ")";
      }
    };
  }

  public static <ValueT> ReadableProperty<Boolean> notEquals(final ReadableProperty<ValueT> prop, final ValueT value) {
    return not(equals(prop, value));
  }

  public static <ValueT> ReadableProperty<Boolean> notEquals(final ReadableProperty<? extends ValueT> p1, final ReadableProperty<? extends ValueT> p2) {
    return not(equals(p1, p2));
  }

  public static <SourceT, TargetT> ReadableProperty<TargetT> transform(final ReadableProperty<SourceT> prop, final Function<SourceT, TargetT> f) {
    return new DerivedProperty<TargetT>(prop) {
      @Override
      public TargetT get() {
        return f.apply(prop.get());
      }

      @Override
      public String getPropExpr() {
        return "transform(" + prop.getPropExpr() + ", " + f + ")";
      }
    };
  }

  public static <SourceT, TargetT> Property<TargetT> transform(final Property<SourceT> prop, final Function<SourceT, TargetT> sToT, final Function<TargetT, SourceT> tToS) {
    class TransformedProperty implements Property<TargetT> {
      @Override
      public TargetT get() {
        return sToT.apply(prop.get());
      }

      @Override
      public Registration addHandler(final EventHandler<PropertyChangeEvent<TargetT>> handler) {
        return prop.addHandler(new EventHandler<PropertyChangeEvent<SourceT>>() {
          @Override
          public void onEvent(PropertyChangeEvent<SourceT> event) {
            TargetT oldValue = sToT.apply(event.getOldValue());
            TargetT newValue = sToT.apply(event.getNewValue());

            if (Objects.equal(oldValue, newValue)) return;

            handler.onEvent(new PropertyChangeEvent<TargetT>(oldValue, newValue));
          }
        });
      }

      @Override
      public void set(TargetT value) {
        prop.set(tToS.apply(value));
      }

      @Override
      public String getPropExpr() {
        return "transform(" + prop.getPropExpr() + ", " + sToT + ", " + tToS + ")";
      }
    }

    return new TransformedProperty();
  }

  public static <ValueT> ReadableProperty<ValueT> constant(final ValueT value) {
    return new BaseReadableProperty<ValueT>() {
      @Override
      public ValueT get() {
        return value;
      }

      @Override
      public Registration addHandler(EventHandler<PropertyChangeEvent<ValueT>> handler) {
        return Registration.EMPTY;
      }

      @Override
      public String getPropExpr() {
        return "constant(" + value + ")";
      }
    };
  }

  public static <ItemT> ReadableProperty<Boolean> isEmpty(final ObservableCollection<ItemT> collection) {
    return new SimpleCollectionProperty<ItemT, Boolean>(collection, collection.isEmpty()) {
      @Override
      public Boolean get() {
        return collection.isEmpty();
      }

      @Override
      public String getPropExpr() {
        return "isEmpty(" + collection + ")";
      }
    };
  }

  public static <ItemT> ReadableProperty<Integer> size(final ObservableCollection<ItemT> collection) {
    return new SimpleCollectionProperty<ItemT, Integer>(collection, collection.size()) {
      @Override
      public Integer get() {
        return collection.size();
      }

      @Override
      public String getPropExpr() {
        return "size(" + collection + ")";
      }
    };
  }

  public static <ItemT> ReadableProperty<Integer> indexOf(
        final ObservableList<ItemT> collection,
        final ReadableProperty<ItemT> item) {
    return simplePropertyWithCollection(collection, item, new Supplier<Integer>() {
      @Override
      public Integer get() {
        return collection.indexOf(item.get());
      }
    });
  }

  public static <ItemT> ReadableProperty<Boolean> contains(
        final ObservableCollection<ItemT> collection,
        final ReadableProperty<ItemT> item) {
    return simplePropertyWithCollection(collection, item, new Supplier<Boolean>() {
      @Override
      public Boolean get() {
        return collection.contains(item.get());
      }
    });
  }

  private static <ItemT, T> ReadableProperty<T> simplePropertyWithCollection(
        final ObservableCollection<ItemT> collection,
        final ReadableProperty<ItemT> item,
        final Supplier<T> supplier) {

    return new BaseDerivedProperty<T>(supplier.get()) {
      private Registration myRegistration;
      private Registration myCollectionRegistration;

      @Override
      public T get() {
        return supplier.get();
      }

      @Override
      protected void doAddListeners() {
        myRegistration = item.addHandler(new EventHandler<PropertyChangeEvent<ItemT>>() {
          @Override
          public void onEvent(PropertyChangeEvent<ItemT> event) {
            somethingChanged();
          }
        });
        myCollectionRegistration = collection.addListener(Properties.<ItemT>simpleAdapter(new Runnable() {
          @Override
          public void run() {
            somethingChanged();
          }
        }));
      }

      @Override
      protected void doRemoveListeners() {
        myRegistration.remove();
        myCollectionRegistration.remove();
      }

      @Override
      public String getPropExpr() {
        return "fromCollection(" + collection + ", " + item + ", " + supplier + ")";
      }
    };
  }

  public static <ItemT> ReadableProperty<Boolean> notEmpty(final ObservableCollection<ItemT> collection) {
    return not(empty(collection));
  }

  public static <ItemT> ReadableProperty<Boolean> empty(final ObservableCollection<ItemT> collection) {
    return new BaseDerivedProperty<Boolean>(collection.isEmpty()) {
      private Registration myCollectionRegistration;

      @Override
      protected void doAddListeners() {
        myCollectionRegistration = collection.addListener(Properties.<ItemT>simpleAdapter(new Runnable() {
          @Override
          public void run() {
            somethingChanged();
          }
        }));
      }

      @Override
      protected void doRemoveListeners() {
        myCollectionRegistration.remove();
      }

      @Override
      public Boolean get() {
        return collection.isEmpty();
      }

      @Override
      public String getPropExpr() {
        return "empty(" + collection + ")";
      }
    };
  }

  private static <ItemT> CollectionAdapter<ItemT> simpleAdapter(final Runnable r) {
    return new CollectionAdapter<ItemT>() {
      @Override
      public void onItemAdded(CollectionItemEvent<ItemT> event) {
        r.run();
      }

      @Override
      public void onItemRemoved(CollectionItemEvent<ItemT> event) {
        r.run();
      }
    };
  }

  public static <ValueT> ReadableProperty<ValueT> ifProp(final ReadableProperty<Boolean> cond, final ReadableProperty<ValueT> ifTrue, final ReadableProperty<ValueT> ifFalse) {
    return new DerivedProperty<ValueT>(cond, ifTrue, ifFalse) {
      @Override
      public ValueT get() {
        return cond.get() ? ifTrue.get() : ifFalse.get();
      }

      @Override
      public String getPropExpr() {
        return "if(" + cond.getPropExpr() + ", " + ifTrue.getPropExpr() + ", " + ifFalse.getPropExpr() + ")";
      }
    };
  }

  public static <ValueT> ReadableProperty<ValueT> firstNotNull(final ReadableProperty<ValueT>... values) {
    return new DerivedProperty<ValueT>(values) {
      @Override
      public ValueT get() {
        for (ReadableProperty<ValueT> v : values) {
          if (v.get() != null) {
            return v.get();
          }
        }
        return null;
      }

      @Override
      public String getPropExpr() {
        StringBuilder result = new StringBuilder();
        result.append("firstNotNull(");

        boolean first = true;
        for (ReadableProperty<?> v : values) {
          if (first) {
            first = false;
          } else {
            result.append(", ");
          }
          result.append(v.getPropExpr());
        }

        return result.toString();
      }
    };
  }

  public static <ValueT> ReadableProperty<Boolean> isPropertyValid(final ReadableProperty<ValueT> source, final Predicate<ValueT> validator) {
    return new DerivedProperty<Boolean>(source) {
      @Override
      public Boolean get() {
        return validator.apply(source.get());
      }

      @Override
      public String getPropExpr() {
        return "isValid(" + source.getPropExpr() + ", " + validator + ")";
      }
    };
  }

  public static <ValueT> Property<ValueT> validatedProperty(final Property<ValueT> source, final Predicate<ValueT> validator) {
    class ValidatedProperty extends DerivedProperty<ValueT> implements Property<ValueT> {
      private ValueT myLastValid = null;

      ValidatedProperty() {
        super(source);
      }

      @Override
      public ValueT get() {
        ValueT sourceValue = source.get();
        if (validator.apply(sourceValue)) {
          myLastValid = sourceValue;
        }
        return myLastValid;
      }

      @Override
      public void set(ValueT value) {
        if (!validator.apply(value)) {
          return;
        }
        source.set(value);
      }

      @Override
      public String getPropExpr() {
        return "validated(" + source.getPropExpr() + ", " + validator + ")";
      }
    }

    return new ValidatedProperty();
  }

  public static ReadableProperty<String> toStringOf(final ReadableProperty<?> p) {
    return new DerivedProperty<String>(p) {
      @Override
      public String get() {
        return "" + p.get();
      }
    };
  }

  private static abstract class SimpleCollectionProperty<ItemT, ValueT> extends BaseDerivedProperty<ValueT> {
    private ObservableCollection<ItemT> myCollection;
    private Registration myRegistration;

    protected SimpleCollectionProperty(ObservableCollection<ItemT> collection, ValueT initialValue) {
      super(initialValue);
      myCollection = collection;
    }

    @Override
    protected void doAddListeners() {
      myRegistration = myCollection.addListener(new CollectionListener<ItemT>() {
        @Override
        public void onItemAdded(CollectionItemEvent<ItemT> event) {
          somethingChanged();
        }

        @Override
        public void onItemRemoved(CollectionItemEvent<ItemT> event) {
          somethingChanged();
        }
      });
    }

    @Override
    protected void doRemoveListeners() {
      myRegistration.remove();
    }
  }
}