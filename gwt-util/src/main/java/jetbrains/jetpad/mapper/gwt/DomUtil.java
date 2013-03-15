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
package jetbrains.jetpad.mapper.gwt;

import com.google.common.base.Objects;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.Node;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.query.client.Function;
import com.google.gwt.query.client.GQuery;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import jetbrains.jetpad.model.event.EventHandler;
import jetbrains.jetpad.model.event.ListenerCaller;
import jetbrains.jetpad.model.event.Listeners;
import jetbrains.jetpad.model.event.Registration;
import jetbrains.jetpad.model.property.Property;
import jetbrains.jetpad.model.property.PropertyChangeEvent;
import jetbrains.jetpad.model.property.WritableProperty;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

import static com.google.gwt.query.client.GQuery.$;

public class DomUtil {
  public static List<Node> elementChildren(final Element e) {
    return new AbstractList<Node>() {
      @Override
      public Node get(int index) {
        return e.getChild(index);
      }

      @Override
      public Node set(int index, Node element) {
        if (element.getParentElement() != null) throw new IllegalStateException();

        Node child = get(index);
        e.replaceChild(child, element);
        return child;
      }

      @Override
      public void add(int index, Node element) {
        if (element.getParentElement() != null) throw new IllegalStateException();

        if (index == 0) {
          e.insertFirst(element);
        } else {
          Node prev = e.getChild(index - 1);
          e.insertAfter(element, prev);
        }
      }

      @Override
      public Node remove(int index) {
        Node child = e.getChild(index);
        e.removeChild(child);
        return child;
      }

      @Override
      public int size() {
        return e.getChildCount();
      }
    };
  }

  public static List<WithElement> withElementChildren(final Element e) {
    final List<WithElement> items = new ArrayList<WithElement>();
    final List<Node> children = elementChildren(e);

    return new AbstractList<WithElement>() {
      @Override
      public WithElement get(int index) {
        return items.get(index);
      }

      @Override
      public WithElement set(int index, WithElement element) {
        WithElement result = items.set(index, element);
        children.set(index, result.getElement());
        return result;
      }

      @Override
      public void add(int index, WithElement element) {
        items.add(index, element);
        children.add(index, element.getElement());
      }

      @Override
      public WithElement remove(int index) {
        WithElement result = items.remove(index);
        children.remove(index);
        return result;
      }

      @Override
      public int size() {
        return items.size();
      }
    };
  }

  public static WritableProperty<String> innerTextOf(final Element e) {
    return new WritableProperty<String>() {
      @Override
      public void set(String value) {
        e.setInnerText(value);
      }
    };
  }

  public static Property<String> editableTextOf(final Element element) {
    return new Property<String>() {
      private String myValue;
      private boolean myEditing;
      private Listeners<EventHandler<PropertyChangeEvent<String>>> myListeners = new Listeners<EventHandler<PropertyChangeEvent<String>>>();
      private InputElement myEditor = DOM.createInputText().cast();

      {
        $(element).click(new Function() {
          @Override
          public boolean f(Event e) {
            startEditing();
            return false;
          }
        });

        $(myEditor).keydown(new Function() {
          @Override
          public boolean f(Event e) {
            if (e.getKeyCode() == KeyCodes.KEY_ENTER) {
              set(myEditor.getValue());
              stopEditing();
              return false;
            }

            if (e.getKeyCode() == KeyCodes.KEY_ESCAPE) {
              stopEditing();
              return false;
            }

            return super.f(e);
          }
        });

        $(myEditor).blur(new Function() {
          @Override
          public boolean f(Event e) {
            if (myEditing) {
              stopEditing();
            }
            return false;
          }
        });
      }

      private void startEditing() {
        if (myEditing) throw new IllegalStateException();
        myEditing = true;

        element.setInnerText("");
        element.appendChild(myEditor);
        myEditor.setValue(myValue);
        myEditor.focus();
      }

      private void stopEditing() {
        if (!myEditing) throw new IllegalStateException();
        myEditing = false;

        myEditor.removeFromParent();
        element.setInnerText(myValue);
      }

      @Override
      public String get() {
        return myValue;
      }

      @Override
      public void set(String value) {
        if (Objects.equal(myValue, value)) return;

        String oldValue = myValue;
        myValue = value;
        if (myEditing) {
          myEditor.setValue(value);
        } else {
          element.setInnerText(value);
        }

        final PropertyChangeEvent<String> event = new PropertyChangeEvent<String>(oldValue,  value);
        myListeners.fire(new ListenerCaller<EventHandler<PropertyChangeEvent<String>>>() {
          @Override
          public void call(EventHandler<PropertyChangeEvent<String>> l) {
            l.onEvent(event);
          }
        });
      }

      @Override
      public Registration addHandler(EventHandler<PropertyChangeEvent<String>> handler) {
        return myListeners.add(handler);
      }

      @Override
      public String getPropExpr() {
        return "editableTextOf(" + element + ")";
      }
    };
  }

  public static Property<Boolean> checkbox(final InputElement element) {
    return new Property<Boolean>() {
      private boolean myHasListener;
      private Listeners<EventHandler<PropertyChangeEvent<Boolean>>> myListeners = new Listeners<EventHandler<PropertyChangeEvent<Boolean>>>();

      @Override
      public Boolean get() {
        return element.isChecked();
      }

      @Override
      public void set(Boolean value) {
        element.setChecked(value);
      }

      @Override
      public Registration addHandler(EventHandler<PropertyChangeEvent<Boolean>> handler) {
        if (myListeners.isEmpty() && !myHasListener) {
          $(element).change(new Function() {
            @Override
            public boolean f(Event e) {
              final PropertyChangeEvent<Boolean> event = new PropertyChangeEvent<Boolean>(!element.isChecked(), element.isChecked());
              myListeners.fire(new ListenerCaller<EventHandler<PropertyChangeEvent<Boolean>>>() {
                @Override
                public void call(EventHandler<PropertyChangeEvent<Boolean>> l) {
                  l.onEvent(event);
                }
              });
              return false;
            }
          });
          myHasListener = true;
        }
        return myListeners.add(handler);
      }

      @Override
      public String getPropExpr() {
        return "checkbox(" + element + ")";
      }
    };
  }

  public static WritableProperty<Boolean> hasClass(final Element el, final String cls) {
    return new WritableProperty<Boolean>() {
      private boolean myValue;

      @Override
      public void set(Boolean value) {
        if (myValue == value) return;
        if (value) {
          el.addClassName(cls);
        } else {
          el.removeClassName(cls);
        }
        myValue = value;
      }
    };
  }
}