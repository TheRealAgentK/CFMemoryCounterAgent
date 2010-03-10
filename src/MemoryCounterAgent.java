package eu.javaspecialists.tjsn.memory;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.util.*;

public class MemoryCounterAgent {
  protected static Instrumentation instrumentation;

  /** Initializes agent */
  public static void premain(String agentArgs,
                             Instrumentation instrumentation) {
    MemoryCounterAgent.instrumentation = instrumentation;
  }

  /** Returns object size. */
  public static long sizeOf(Object obj) {
    if (instrumentation == null) {
      throw new IllegalStateException(
          "Instrumentation environment not initialised.");
    }
    if (isSharedFlyweight(obj)) {
      return 0;
    }
    return instrumentation.getObjectSize(obj);
  }

  /**
   * Returns deep size of object, recursively iterating over
   * its fields and superclasses.
   */
  public static long deepSizeOf(Object obj) {
    Map visited = new IdentityHashMap();
    Stack stack = new Stack();
    stack.push(obj);

    long result = 0;
    long newSize = 0;
    do {
      newSize = internalSizeOf(stack.pop(), stack, visited);   
      result = result + newSize;
    } while (!stack.isEmpty());
    return result;
  }

  /**
   * Returns true if this is a well-known shared flyweight.
   * For example, interned Strings, Booleans and Number objects
   */
  protected static boolean isSharedFlyweight(Object obj) {
    // optimization - all of our flyweights are Comparable
    if (obj instanceof Comparable) {
      if (obj instanceof Enum) {
        return true;
      } else if (obj instanceof String) {
        return (obj == ((String) obj).intern());
      } else if (obj instanceof Boolean) {
        return (obj == Boolean.TRUE || obj == Boolean.FALSE);
      } else if (obj instanceof Integer) {
        return (obj == Integer.valueOf((Integer) obj));
      } else if (obj instanceof Short) {
        return (obj == Short.valueOf((Short) obj));
      } else if (obj instanceof Byte) {
        return (obj == Byte.valueOf((Byte) obj));
      } else if (obj instanceof Long) {
        return (obj == Long.valueOf((Long) obj));
      } else if (obj instanceof Character) {
        return (obj == Character.valueOf((Character) obj));
      }
    }
    return false;
  }

  private static boolean skipObject(Object obj, Map visited) {
    return obj == null
        || visited.containsKey(obj)
        || isSharedFlyweight(obj);
  }

  private static long internalSizeOf(Object obj, Stack stack, Map visited) 
  {
    if (skipObject(obj, visited)) 
    {
      return 0;
    }
   	
    Class clazz = obj.getClass();  
    	
    if (clazz.isArray()) 
    {
      addArrayElementsToStack(clazz, obj, stack);
    } 
    else 
    {
      // add all non-primitive fields to the stack
      while (clazz != null) 
      {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) 
        {
          if (!Modifier.isStatic(field.getModifiers()) && !field.getType().isPrimitive()) 
          {
            field.setAccessible(true);
            try 
          	{
              stack.add(field.get(obj));
            } 
            catch (IllegalAccessException ex) 
            {
              throw new RuntimeException(ex);
            }
          }
        }
        clazz = clazz.getSuperclass();
      }
    }
    visited.put(obj, null);

    return sizeOf(obj);
  }

  protected static void addArrayElementsToStack(Class clazz, Object obj, Stack stack) {
    if (!clazz.getComponentType().isPrimitive()) {
      int length = Array.getLength(obj);
      for (int i = 0; i < length; i++) {
        stack.add(Array.get(obj, i));
      }
    }
  }
}