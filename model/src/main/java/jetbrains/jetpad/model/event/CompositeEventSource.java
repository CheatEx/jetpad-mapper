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
package jetbrains.jetpad.model.event;

import java.util.ArrayList;
import java.util.List;

public class CompositeEventSource<EventT> implements EventSource<EventT> {
  private Listeners<EventHandler<EventT>> myHandlers = new Listeners<EventHandler<EventT>>();
  private List<EventSource<? extends EventT>> myEventSources = new ArrayList<EventSource<? extends EventT>>();
  private List<Registration> myRegistrations = new ArrayList<Registration>();

  public CompositeEventSource(EventSource<? extends EventT>... sources) {
    for (EventSource<? extends EventT> s : sources) {
      add(s);
    }
  }

  public void add(EventSource<? extends EventT> source) {
    myEventSources.add(source);
  }

  public void remove(EventSource<? extends EventT> source) {
    myEventSources.remove(source);
  }

  @Override
  public Registration addHandler(final EventHandler<EventT> handler) {
    if (myHandlers.isEmpty()) {
      for (EventSource<? extends EventT> src : myEventSources) {
        addHandlerTo(src);
      }
    }

    final Registration reg = myHandlers.add(handler);
    return new Registration() {
      @Override
      public void remove() {
        reg.remove();
        if (myHandlers.isEmpty()) {
          for (Registration hr : myRegistrations) {
            hr.remove();
          }
          myRegistrations.clear();
        }
      }
    };
  }

  private <PartEventT extends EventT> void addHandlerTo(EventSource<PartEventT> src) {
    myRegistrations.add(src.addHandler(new EventHandler<PartEventT>() {
      @Override
      public void onEvent(final PartEventT event) {
        myHandlers.fire(new ListenerCaller<EventHandler<EventT>>() {
          @Override
          public void call(EventHandler<EventT> item) {
            item.onEvent(event);
          }
        });
      }
    }));
  }
}