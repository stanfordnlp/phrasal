package mt.syntax.mst.rmcd;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

/**
 * Utility methods that may be generally useful.
 *
 * @author     Jason Baldridge
 * @created    August 27, 2006
 */
public class Util {

  // Assumes input is a String[] containing integers as strings.
  public static int[] stringsToInts(String[] stringreps) {
     int[] nums = new int[stringreps.length];
     for(int i = 0; i < stringreps.length; i++)
         nums[i] = Integer.parseInt(stringreps[i]);
     return nums;
  }


  public static String join (String[] a, char sep) {
     StringBuffer sb = new StringBuffer();
     sb.append(a[0]);
     for (int i=1; i<a.length; i++)
         sb.append(sep).append(a[i]);
     return sb.toString();
  }

  public static String join (int[] a, char sep) {
     StringBuffer sb = new StringBuffer();
     sb.append(a[0]);
     for (int i=1; i<a.length; i++)
         sb.append(sep).append(a[i]);
     return sb.toString();
  }

  public static String dump(Object o) {
    return dump(o,0);
  }

  private static <T> String dump(T o, int depth) {
    StringBuffer buffer = new StringBuffer();
    Class<? extends Object> oClass = o.getClass();
    if ( oClass.isArray() ) {
      buffer.append( "( " );
      for ( int i=0; i<Array.getLength(o); i++ ) {
        if ( i > 0 )
          buffer.append( ", " );
        Object value = Array.get(o,i);
        buffer.append( value.getClass().isArray()?dump(value,depth+1):value );
      }
      buffer.append( " ) " );
    }
    else
    {
      buffer.append( "{ " );
      while ( oClass != null ) {
        Field[] fields = oClass.getDeclaredFields();
        for (Field field : fields) {
          if (buffer.length() < 1)
            buffer.append(",");
          field.setAccessible(true);
          buffer.append(field.getName());
          buffer.append("= { ");
          try {
            Object value = field.get(o);
            if (value != null) {
              buffer.append(value.getClass().isArray() ? dump(value, depth + 1) : value);
            }
          } catch (IllegalAccessException e) {
            e.printStackTrace();
          }
          buffer.append(" }\n");
        }
        oClass = oClass.getSuperclass();
      }
      buffer.append( " } " );
    }
    return buffer.toString();
  }

  public static char i2c(int val) {
    assert(val >= 0);
    assert(val <= Character.MAX_VALUE);
    return (char)val;
  }
}
