/*
 * Copyright 2012-2015 JetBrains s.r.o
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
package jetbrains.jetpad.model.id;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class BaseIdTest {

  @Test
  public void idEquality() {
    MyId id1 = new MyId("abc.def");
    MyId id2 = new MyId("abc.defz");
    MyId id3 = new MyId("abc.def");

    assertEquals(id1, id3);
    assertFalse(id2.equals(id3));
  }

  @Test(expected = IllegalStateException.class)
  public void duplicateName() {
    new MyId("a.b", "newName1");
    new MyId("a.b", "newName2");
  }

  @Test
  public void nameDiscovery () {
    new MyId("a.b", "newName1");
    MyId id2 = new MyId("a.b");

    assertEquals("newName1 [a.b]", id2.toString());
  }

  @Test(expected = IllegalArgumentException.class)
  public void emptyId() {
    new MyId("");
  }

  @Test(expected = IllegalArgumentException.class)
  public void nullId() {
    new MyId(null);
  }

  private static class MyId extends BaseId {
    private MyId() {
    }

    private MyId(String id) {
      super(id);
    }

    private MyId(String id, String name) {
      super(id, name);
    }
  }
}