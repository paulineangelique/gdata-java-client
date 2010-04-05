/* Copyright (c) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.api.client;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Memory-efficient map of keys to values with list-style random-access
 * semantics.
 * <p>
 * Conceptually, the keys and values are stored in a simpler array in order to
 * minimize memory use and provide for fast access to a key/value at a certain
 * index (for example {@link #getKey(int)}). However, traditional mapping
 * operations like {@link #get(Object)} and {@link #put(Object, Object)} are
 * slower because they need to look up all key/value pairs in the worst case.
 */
public class ArrayMap<K, V> extends AbstractMap<K, V> implements Cloneable {
  private int size;
  private Object[] data;
  private volatile EntrySet entrySet;

  private ArrayMap() {
  }

  /** Returns a new instanceof of a slim map with initial capacity of zero. */
  public static <K, V> ArrayMap<K, V> create() {
    return new ArrayMap<K, V>();
  }

  /** Returns a new instanceof of a slim map of the given initial capacity. */
  public static <K, V> ArrayMap<K, V> create(int initialCapacity) {
    ArrayMap<K, V> result = create();
    result.ensureCapacity(initialCapacity);
    return result;
  }

  /** Returns the number of key-value pairs set. */
  @Override
  public int size() {
    return this.size;
  }

  /** Returns the key at the given index or {@code null} if out of bounds. */
  public K getKey(int index) {
    if (index < 0 || index >= this.size) {
      return null;
    }
    @SuppressWarnings("unchecked")
    K result = (K) this.data[index << 1];
    return result;
  }

  /** Returns the value at the given index or {@code null} if out of bounds. */
  public V getValue(int index) {
    if (index < 0 || index >= this.size) {
      return null;
    }
    return valueAtDataIndex(1 + (index << 1));
  }

  /**
   * Sets the key/value mapping at the given index, overriding any existing
   * key/value mapping.
   * <p>
   * There is no checking done to ensure that the key does not already exist.
   * Therefore, this method is dangerous to call unless the caller can be
   * certain the key does not already exist in the map.
   * 
   * @return previous value or {@code null} for none
   * @throws IndexOutOfBoundsException if index is negative
   */
  public V set(int index, K key, V value) {
    if (index < 0) {
      throw new IndexOutOfBoundsException();
    }
    int minSize = index + 1;
    ensureCapacity(minSize);
    int dataIndex = index << 1;
    V result = valueAtDataIndex(dataIndex + 1);
    setData(dataIndex, key, value);
    if (minSize > this.size) {
      this.size = minSize;
    }
    return result;
  }

  /**
   * Sets the value at the given index, overriding any existing value mapping.
   * 
   * @return previous value or {@code null} for none
   * @throws IndexOutOfBoundsException if index is negative or {@code >=} size
   */
  public V set(int index, V value) {
    int size = this.size;
    if (index < 0 || index >= size) {
      throw new IndexOutOfBoundsException();
    }
    int valueDataIndex = 1 + index << 1;
    V result = valueAtDataIndex(valueDataIndex);
    this.data[valueDataIndex] = value;
    return result;
  }

  /**
   * Adds the key/value mapping at the end of the list. Behaves identically to
   * {@code set(size(), key, value)}.
   * 
   * @throws IndexOutOfBoundsException if index is negative
   */
  public void add(K key, V value) {
    set(this.size, key, value);
  }

  /**
   * Removes the key/value mapping at the given index, or ignored if the index
   * is out of bounds.
   * 
   * @return previous value or {@code null} for none
   */
  public V remove(int index) {
    return removeFromDataIndexOfKey(index << 1);
  }

  /** Returns whether there is a mapping for the given key. */
  @Override
  public boolean containsKey(Object key) {
    return -2 != getDataIndexOfKey(key);
  }

  /** Returns the index of the given key or {@code -1} if there is no such key. */
  public int getIndexOfKey(K key) {
    return getDataIndexOfKey(key) >> 1;
  }

  /**
   * Returns the value set for the given key or {@code null} if there is no such
   * mapping or if the mapping value is {@code null}.
   */
  @Override
  public V get(Object key) {
    return valueAtDataIndex(getDataIndexOfKey(key) + 1);
  }

  /**
   * Sets the value for the given key, overriding any existing value.
   * 
   * @return previous value or {@code null} for none
   */
  @Override
  public V put(K key, V value) {
    int index = getIndexOfKey(key);
    if (index == -1) {
      index = this.size;
    }
    return set(index, key, value);
  }

  /**
   * Removes the key-value pair of the given key, or ignore if the key cannot be
   * found.
   * 
   * @return previous value or {@code null} for none
   */
  @Override
  public V remove(Object key) {
    return removeFromDataIndexOfKey(getDataIndexOfKey(key));
  }

  /** Trims the internal array storage to minimize memory usage. */
  public void trim() {
    setDataCapacity(this.size << 1);
  }

  /**
   * Ensures that the capacity of the internal arrays is at least a given
   * capacity.
   */
  public void ensureCapacity(int minCapacity) {
    Object[] data = this.data;
    int minDataCapacity = minCapacity << 1;
    int oldDataCapacity = data == null ? 0 : data.length;
    if (minDataCapacity > oldDataCapacity) {
      int newDataCapacity = oldDataCapacity / 2 * 3 + 1;
      if (newDataCapacity % 2 == 1) {
        newDataCapacity++;
      }
      if (newDataCapacity < minDataCapacity) {
        newDataCapacity = minDataCapacity;
      }
      setDataCapacity(newDataCapacity);
    }
  }

  private void setDataCapacity(int newDataCapacity) {
    if (newDataCapacity == 0) {
      this.data = null;
      return;
    }
    int size = this.size;
    Object[] oldData = this.data;
    if (size == 0 || newDataCapacity != oldData.length) {
      Object[] newData = this.data = new Object[newDataCapacity];
      if (size != 0) {
        System.arraycopy(oldData, 0, newData, 0, size << 1);
      }
    }
  }

  private void setData(int dataIndexOfKey, K key, V value) {
    Object[] data = this.data;
    data[dataIndexOfKey] = key;
    data[dataIndexOfKey + 1] = value;
  }

  private V valueAtDataIndex(int dataIndex) {
    if (dataIndex < 0) {
      return null;
    }
    @SuppressWarnings("unchecked")
    V result = (V) this.data[dataIndex];
    return result;
  }

  /**
   * Returns the data index of the given key or {@code -2} if there is no such
   * key.
   */
  private int getDataIndexOfKey(Object key) {
    int dataSize = this.size << 1;
    Object[] data = this.data;
    for (int i = 0; i < dataSize; i += 2) {
      Object k = data[i];
      if (key == null ? k == null : key.equals(k)) {
        return i;
      }
    }
    return -2;
  }

  /**
   * Removes the key/value mapping at the given data index of key, or ignored if
   * the index is out of bounds.
   */
  private V removeFromDataIndexOfKey(int dataIndexOfKey) {
    int dataSize = this.size << 2;
    if (dataIndexOfKey < 0 || dataIndexOfKey >= dataSize) {
      return null;
    }
    V result = valueAtDataIndex(dataIndexOfKey + 1);
    Object[] data = this.data;
    int moved = dataSize - dataIndexOfKey + 2;
    if (moved != 0) {
      System.arraycopy(data, dataIndexOfKey + 2, data, dataIndexOfKey, moved);
    }
    setData(dataIndexOfKey, null, null);
    return result;
  }

  @Override
  public void clear() {
    this.size = 0;
    this.data = null;
  }

  @Override
  public boolean containsValue(Object value) {
    int dataSize = this.size << 1;
    Object[] data = this.data;
    for (int i = 1; i < dataSize; i += 2) {
      Object v = data[i];
      if (value == null ? v == null : value.equals(v)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Set<Map.Entry<K, V>> entrySet() {
    EntrySet entrySet = this.entrySet;
    if (entrySet == null) {
      entrySet = this.entrySet = new EntrySet();
    }
    return entrySet;
  }

  @Override
  public ArrayMap<K, V> clone() {
    try {
      @SuppressWarnings("unchecked")
      ArrayMap<K, V> result = (ArrayMap<K, V>) super.clone();
      return result;
    } catch (CloneNotSupportedException e) {
      // won't happen
      return null;
    }
  }

  final class EntrySet extends AbstractSet<Map.Entry<K, V>> {

    @Override
    public Iterator<Map.Entry<K, V>> iterator() {
      return new EntryIterator();
    }

    @Override
    public int size() {
      return ArrayMap.this.size;
    }
  }

  final class EntryIterator implements Iterator<Map.Entry<K, V>> {

    private boolean removed;
    private int nextIndex;

    public final boolean hasNext() {
      return this.nextIndex < ArrayMap.this.size;
    }

    public final Map.Entry<K, V> next() {
      int index = this.nextIndex;
      if (index == ArrayMap.this.size) {
        throw new NoSuchElementException();
      }
      this.nextIndex++;
      return new Entry(index);
    }

    public final void remove() {
      int index = this.nextIndex - 1;
      if (this.removed || index < 0) {
        throw new IllegalArgumentException();
      }
      ArrayMap.this.remove(index);
      this.removed = true;
    }
  }

  final class Entry implements Map.Entry<K, V> {

    private int index;

    Entry(int index) {
      this.index = index;
    }

    public K getKey() {
      return ArrayMap.this.getKey(this.index);
    }

    public V getValue() {
      return ArrayMap.this.getValue(this.index);
    }

    public V setValue(V value) {
      return ArrayMap.this.set(this.index, value);
    }
  }
}
