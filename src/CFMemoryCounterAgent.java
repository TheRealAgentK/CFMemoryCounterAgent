package nz.co.ventegocreative.coldfusion.memory;

import eu.javaspecialists.tjsn.memory.MemoryCounterAgent;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.util.*;

public class CFMemoryCounterAgent extends MemoryCounterAgent {

  /** Returns object size catering for either flyweights or not */
  public static long sizeOf(Object obj, boolean ignoreFlyweights) {

    if (instrumentation == null) {
      throw new IllegalStateException("Instrumentation environment not initialised.");
    }
    
    if (ignoreFlyweights == true && isSharedFlyweight(obj)) {
      return 0;
    }
   
    return instrumentation.getObjectSize(obj);
  }

  /**
   * Returns deep size of object, recursively iterating over
   * its fields and superclasses.
   */
  public static long deepSizeOf(Object obj, boolean ignoreFlyweights) {
    Map visited = new IdentityHashMap();
    Stack stack = new Stack();
    stack.push(obj);
    
    long result = 0;
    long newSize = 0;
    
    do {
      newSize = internalSizeOf(stack.pop(), stack, visited, ignoreFlyweights); 
      result = result + newSize;
    } while (!stack.isEmpty());
   
    return result;
  }

  private static boolean skipObject(Object obj, Map visited, boolean ignoreFlyweights) {
    return obj == null
        || visited.containsKey(obj)
        || (ignoreFlyweights == true && isSharedFlyweight(obj));
  }

  private static long internalSizeOf(Object obj, Stack stack, Map visited, boolean ignoreFlyweights) 
  {
    if (skipObject(obj, visited, ignoreFlyweights)) 
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
      // add all non-primitive fields, non-CF memory tracker objects and non-SessionContext objects to the stack
      while (clazz != null) 
      {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) 
        {
          if (!Modifier.isStatic(field.getModifiers()) && !field.getType().isPrimitive() && 
            field.getType().getName() != "coldfusion.runtime.NeoPageContext" && 
            field.getType().getName() != "coldfusion.runtime.CfJspPage" && 
            field.getType().getName() != "coldfusion.monitor.memory.MemoryTrackable" && 
            field.getType().getName() != "coldfusion.monitor.sql.QueryStat" && 
            field.getType().getName() != "coldfusion.monitor.memory.MemoryTrackerProxy" && 
            field.getType().getName() != "javax.servlet.ServletContext") 
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

    return sizeOf(obj, ignoreFlyweights);
  }
}