package org.infinispan.commons.util;

import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

import org.infinispan.commons.logging.Log;
import org.infinispan.commons.logging.LogFactory;

/**
 * Type-aware properties.  Extends the JDK {@link Properties} class to provide accessors that convert values to certain
 * types, using default values if a conversion is not possible.
 *
 * @author Manik Surtani
 * @since 4.0
 */
public class TypedProperties extends Properties {

   /** The serialVersionUID */
   private static final long serialVersionUID = 3799321248100686287L;

   private static final Log log = LogFactory.getLog(TypedProperties.class);

   /**
    * Copy constructor
    *
    * @param p properties instance to from.  If null, then it is treated as an empty Properties instance.
    */
   public TypedProperties(Map<?, ?> p) {
      if (p != null && !p.isEmpty()) super.putAll(p);
   }

   /**
    * Default constructor that returns an empty instance
    */
   public TypedProperties() {

   }

   /**
    * Factory method that converts a JDK {@link Map} (including {@link Properties} instance to an instance of TypedProperties, if needed.
    *
    * @param p properties to convert.
    * @return A TypedProperties object.  Returns an empty TypedProperties instance if p is null.
    */
   public static TypedProperties toTypedProperties(Map<?, ?> p) {
      if (p instanceof TypedProperties) return (TypedProperties) p;
      return new TypedProperties(p);
   }

   public int getIntProperty(String key, int defaultValue) {
      return getIntProperty(key, defaultValue, false);
   }

   public int getIntProperty(String key, int defaultValue, boolean doStringReplace) {
      Object value = this.get(key);
      if (value instanceof Integer) {
         return (int) value;
      } else {
         return getPropertyFn(value, defaultValue, doStringReplace, valueStr -> {
            try {
               return Integer.parseInt(valueStr);
            } catch (NumberFormatException nfe) {
               log.unableToConvertStringPropertyToInt(valueStr, defaultValue);
               return defaultValue;
            }
         });
      }
   }

   public long getLongProperty(String key, long defaultValue) {
      return getLongProperty(key, defaultValue, false);
   }

   public long getLongProperty(String key, long defaultValue, boolean doStringReplace) {
      Object value = this.get(key);
      if (value instanceof Long) {
         return (long) value;
      } else {
         return getPropertyFn(value, defaultValue, doStringReplace, valueStr -> {
            try {
               return Long.parseLong(valueStr);
            } catch (NumberFormatException nfe) {
               log.unableToConvertStringPropertyToLong(valueStr, defaultValue);
               return defaultValue;
            }
         });
      }
   }

   public long getDurationProperty(String key, long defaultValue) {
      return getDurationProperty(key, defaultValue, false);
   }

   public long getDurationProperty(String key, long defaultValue, boolean doStringReplace) {
      Object value = this.get(key);
      if (value instanceof Long) {
         return (long) value;
      } else {
         return getPropertyFn(value, defaultValue, doStringReplace, valueStr -> {
            try {
               return TimeQuantity.valueOf(valueStr).longValue();
            } catch (NumberFormatException nfe) {
               log.unableToConvertStringPropertyToLong(valueStr, defaultValue);
               return defaultValue;
            }
         });
      }
   }

   public boolean getBooleanProperty(String key, boolean defaultValue) {
      return getBooleanProperty(key, defaultValue, false);
   }

   public boolean getBooleanProperty(String key, boolean defaultValue, boolean doStringReplace) {
      Object value = this.get(key);
      if (value instanceof Boolean) {
         return (boolean) value;
      } else {
         return getPropertyFn(value, defaultValue, doStringReplace, valueStr -> {
            try {
               return Boolean.parseBoolean(valueStr);
            } catch (Exception e) {
               log.unableToConvertStringPropertyToBoolean(valueStr, defaultValue);
               return defaultValue;
            }
         });
      }
   }

   public <T extends Enum<T>> T getEnumProperty(String key, Class<T> enumClass, T defaultValue) {
      return getEnumProperty(key, enumClass, defaultValue, false);
   }

   public <T extends Enum<T>> T getEnumProperty(String key, Class<T> enumClass, T defaultValue, boolean doStringReplace) {
      Object value = this.get(key);
      if (value instanceof Enum && enumClass.isInstance(value)) {
         return (T) value;
      } else {
         return getPropertyFn(value, defaultValue, doStringReplace, valueStr -> {
            try {
               return Enum.valueOf(enumClass, valueStr);
            } catch (IllegalArgumentException e) {
               log.unableToConvertStringPropertyToEnum(valueStr, defaultValue.name());
               return defaultValue;
            }
         });
      }
   }

   /**
    * Get the property associated with the key, optionally applying string property replacement as defined in
    * {@link StringPropertyReplacer#replaceProperties} to the result.
    *
    * @param   key               the hashtable key.
    * @param   defaultValue      a default value.
    * @param   doStringReplace   boolean indicating whether to apply string property replacement
    * @return  the value in this property list with the specified key value after optionally being inspected for String property replacement
    */
   public synchronized String getProperty(String key, String defaultValue, boolean doStringReplace) {
      if (doStringReplace)
         return StringPropertyReplacer.replaceProperties(getProperty(key, defaultValue));
      else
         return getProperty(key, defaultValue);
   }

   /**
    * Get the property associated with the key, optionally applying string property replacement as defined in
    * {@link StringPropertyReplacer#replaceProperties} to the result.
    *
    * @param   key               the hashtable key.
    * @param   doStringReplace   boolean indicating whether to apply string property replacement
    * @return  the value in this property list with the specified key value after optionally being inspected for String property replacement
    */
   public synchronized String getProperty(String key, boolean doStringReplace) {
      if (doStringReplace)
         return StringPropertyReplacer.replaceProperties(getProperty(key));
      else
         return getProperty(key);
   }

   /**
    * Put a value if the associated key is not present
    * @param key new key
    * @param value new value
    * @return this TypedProperties instance for method chaining
    *
    */
   public synchronized TypedProperties putIfAbsent(String key, String value) {
      if (getProperty(key) == null) {
         put(key, value);
      }
      return this;
   }

   @Override
   public synchronized TypedProperties setProperty(String key, String value) {
      super.setProperty(key, value);
      return this;
   }

   public synchronized TypedProperties setProperty(String key, int value) {
      super.setProperty(key, Integer.toString(value));
      return this;
   }

   public synchronized TypedProperties setProperty(String key, long value) {
      super.setProperty(key, Long.toString(value));
      return this;
   }

   public synchronized TypedProperties setProperty(String key, boolean value) {
      super.setProperty(key, Boolean.toString(value));
      return this;
   }

   private <V> V getPropertyFn(Object value, V defaultValue, boolean doStringReplace, Function<String, V> action) {
      String valueStr = null;
      if (value instanceof String) {
         valueStr = (String) value;
      }
      if (valueStr == null) return defaultValue;
      valueStr = valueStr.trim();
      if (valueStr.isEmpty()) return defaultValue;

      if (doStringReplace)
         valueStr = StringPropertyReplacer.replaceProperties(valueStr);
      return action.apply(valueStr);
   }
}
